package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.NEW_RECT_CARPET_FLAGS
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

/*
 * Map editing commands (legacy V1 robots).
 *
 * Every payload below is copied from a capture of the official Roborock app editing an S8 Pro
 * Ultra through local_roborock_server (decompiled_mqtt.jsonl, 2026-09-28). The capture overrides
 * what older firmware used: on this firmware the
 * commands take a `{"data": ...}` object, several add `"need_retry": 1` (answered first with
 * `["retry"]`, then with the real result), and the app brackets edits with start_edit_map /
 * end_edit_map. All coordinates are robot millimetres, the same space the map blocks use.
 *
 * Older firmware takes the same commands without the `{"data": ...}` object; [sendDataCommand]
 * falls back to that.
 *
 * Deliberately missing until a capture shows it: no-mop zones.
 */

/** The robot answered a command with an error object. */
class RobotCommandException(val method: String, val error: JsonObject) :
    RuntimeException(robotErrorText(method, error))

private fun robotErrorText(method: String, error: JsonObject): String {
    val message = runCatching { error["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    val code = runCatching { error["code"]?.jsonPrimitive?.contentOrNull }.getOrNull()
    return "The robot refused $method" + (message?.let { ": $it" } ?: "") + (code?.let { " (code $it)" } ?: "")
}

/** Invalid edit caught before anything is sent to the robot. */
class MapEditException(message: String) : RuntimeException(message)

fun V1Response.requireOk(method: String): V1Response {
    error?.let { throw RobotCommandException(method, it) }
    return this
}

/** Largest coordinate the robot's map can hold: 1024 pixels of 50 mm. */
internal const val MAX_MAP_COORD_MM = 51_200

/** `save_map` refuses more than this many vertices in total (walls count 2, zones 4). */
internal const val MAX_RESTRICTION_VERTICES = 68

internal const val SAVE_MAP_TYPE_NO_GO = 0
internal const val SAVE_MAP_TYPE_WALL = 1

/**
 * Trailing entry the official app always appends to save_map, `[100, mapFlag]` of the map being
 * edited: captured as `[100, 0]` on a robot whose only map had mapFlag 0, and `[100, 1]` worked on a
 * Qrevo S Pro whose only map is map 1 (issue #88).
 */
internal const val SAVE_MAP_TYPE_MAP_INDEX = 100

/**
 * The map index save_map needs, from the robot's map list: known only when the robot holds exactly
 * one map (the captured case). Null otherwise.
 */
fun saveMapIndex(floorMaps: List<FloorMap>): Int? = floorMaps.singleOrNull()?.mapFlag

/**
 * Reasons [map]'s restrictions can't be saved safely, or null when they can. save_map replaces
 * the robot's whole set, so anything on the map this app can't write back must block it.
 * Thresholds and carpets are unaffected: the official app's save_map never carries them, they
 * have their own commands.
 */
fun restrictionSaveBlocker(map: ParsedMap, mapIndex: Int?): String? = mapIndexBlocker(mapIndex) ?: when {
    map.noMopZones.isNotEmpty() ->
        "This map has no-mop zones. LocalRock can't write them back yet, so saving here could delete them."
    MOP_ONLY_AREAS_BLOCK in map.unmodeledRestrictionBlocks ->
        "This map has mop-only areas. LocalRock can't write them back yet, so saving here could delete them."
    else -> null
}

private const val MOP_ONLY_AREAS_BLOCK = 23

/**
 * Map edits name the map they change by its mapFlag (save_map's `[100, n]`, and `"map_index": n` on
 * carpets and thresholds). The map being edited is only certain when the robot holds exactly one map.
 * That map need not be map 0: a robot that once had a second floor can keep its only map as map 1.
 */
fun mapIndexBlocker(mapIndex: Int?): String? =
    if (mapIndex == null) "Saving map edits is only supported on robots with a single saved map for now." else null

fun restrictionVertexCount(walls: Int, zones: Int): Int = walls * 2 + zones * 4

private fun coord(v: Int): JsonPrimitive {
    if (v !in 0..MAX_MAP_COORD_MM) throw MapEditException("Coordinate $v mm is outside the map")
    return JsonPrimitive(v)
}

private fun quad(z: MapZone): List<JsonPrimitive> =
    listOf(coord(z.x0), coord(z.y0), coord(z.x1), coord(z.y1), coord(z.x2), coord(z.y2), coord(z.x3), coord(z.y3))

private fun withRetry(data: JsonElement) = JsonObject(mapOf("data" to data, "need_retry" to JsonPrimitive(1)))

/** The robot's answer to params in a shape it does not take (S7: "Param is not an array"). */
internal const val PARAM_FORMAT_REFUSED_CODE = -10005

/** Firmware that knows the wrapped form acknowledged it with `["retry"]` within a second (captured). */
private const val RETRY_ACK_TIMEOUT_MS = 6_000L

/** Which params shape a robot takes for the commands sent through [sendDataCommand]. */
internal class DataParamsFormat {
    /** True once the robot has taken a command as plain params. */
    var plain = false
}

internal fun V1Response.errorCode(): Int? =
    runCatching { error?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()

/**
 * Send a command whose captured params are `{"data": <data>, "need_retry": 1}`. Older firmware
 * takes [data] alone as the params:
 * - an S7 on 02.15.78 refused the wrapped save_map with -10005 "Param is not an array" and applied
 *   the plain save_map, split_segment and merge_segment (tested on the robot by the reporter of
 *   issue #14);
 * - an S5 Max never answered the wrapped load_multi_map (issue #11).
 *
 * So the wrapped form goes first, and the plain one follows when the robot refuses the shape or
 * stays silent: [send] throws [RobotSilentException] when not even the `["retry"]` arrives. A
 * refused or ignored command changed nothing, so sending it again is safe. Once the plain form
 * has been taken, [format] keeps the robot on it.
 */
internal suspend fun sendDataCommand(
    format: DataParamsFormat,
    data: JsonElement,
    send: suspend (params: JsonElement, wrapped: Boolean) -> V1Response,
): V1Response {
    if (!format.plain) {
        val answer = try {
            send(withRetry(data), true)
        } catch (e: RobotSilentException) {
            null
        }
        if (answer != null && answer.errorCode() != PARAM_FORMAT_REFUSED_CODE) return answer
    }
    val answer = send(data, false)
    if (answer.errorCode() != PARAM_FORMAT_REFUSED_CODE) format.plain = true
    return answer
}

internal suspend fun VacuumSession.sendData(method: String, data: JsonElement): V1Response =
    sendDataCommand(dataParamsFormat, data) { params, wrapped ->
        sendCommandRaw(
            method, params,
            timeoutMs = MAP_EDIT_TIMEOUT_MS,
            firstReplyTimeoutMs = if (wrapped) RETRY_ACK_TIMEOUT_MS else null,
        )
    }.requireOk(method)

/**
 * `{"data": [walls..., no-go zones..., [100, mapIndex]], "need_retry": 1}`, the order the official
 * app used. Zone corners are written in the order they were read so an untouched zone round-trips.
 */
internal fun encodeSaveMapParams(noGoZones: List<MapZone>, walls: List<VirtualWall>, mapIndex: Int): JsonObject =
    withRetry(saveMapData(noGoZones, walls, mapIndex))

/**
 * `[walls..., no-go zones..., [100, mapIndex]]`. The S7 takes the same list as its plain params,
 * `[100, mapIndex]` included, and replaces its whole set with it like the S8 does (issue #14).
 */
internal fun saveMapData(noGoZones: List<MapZone>, walls: List<VirtualWall>, mapIndex: Int): JsonArray {
    val vertices = restrictionVertexCount(walls.size, noGoZones.size)
    if (vertices > MAX_RESTRICTION_VERTICES) {
        throw MapEditException("Too many restrictions: $vertices of $MAX_RESTRICTION_VERTICES points used")
    }
    val entries = buildList {
        walls.forEach { w ->
            add(JsonArray(listOf(JsonPrimitive(SAVE_MAP_TYPE_WALL), coord(w.x0), coord(w.y0), coord(w.x1), coord(w.y1))))
        }
        noGoZones.forEach { add(JsonArray(listOf(JsonPrimitive(SAVE_MAP_TYPE_NO_GO)) + quad(it))) }
        add(JsonArray(listOf(JsonPrimitive(SAVE_MAP_TYPE_MAP_INDEX), JsonPrimitive(mapIndex))))
    }
    return JsonArray(entries)
}

/** `{"data": [segment, xA, yA, xB, yB], "need_retry": 1}` */
internal fun encodeSplitSegmentParams(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): JsonObject =
    withRetry(splitSegmentData(segment, xA, yA, xB, yB))

internal fun splitSegmentData(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): JsonArray =
    JsonArray(listOf(JsonPrimitive(segment), coord(xA), coord(yA), coord(xB), coord(yB)))

/** Direction value the official app sends for materials that have none. */
internal const val NO_DIRECTION = -1

/** `{"data": [[segment, material, direction]]}`; direction is -1 unless the floor is wood. */
internal fun encodeGroundMaterialParams(segment: Int, materialId: Int, direction: Int?): JsonObject =
    JsonObject(mapOf("data" to JsonArray(listOf(JsonArray(listOf(
        JsonPrimitive(segment), JsonPrimitive(materialId), JsonPrimitive(direction ?: NO_DIRECTION),
    ))))))

/** One row of the robot's segment table, as `get_room_mapping` returns it. */
data class SegmentEntry(val segment: Int, val roomId: String, val tagId: Int)

/**
 * The robot's `get_room_mapping` answer as `[segment, roomId, tag]` rows. An empty list means no
 * room has been assigned yet (S7 Max Ultra, Qrevo Edge, #33). Null means the answer is not a table
 * this app can write back whole, so name_segment must not be sent.
 */
internal fun parseSegmentTable(result: JsonElement?): List<SegmentEntry>? {
    val rows = result as? JsonArray ?: return null
    return rows.map { row ->
        if (row !is JsonArray || row.size < 3) return null
        val segment = (row[0] as? JsonPrimitive)?.intOrNull ?: return null
        val roomId = (row[1] as? JsonPrimitive)?.content ?: return null
        val tag = (row[2] as? JsonPrimitive)?.intOrNull ?: return null
        SegmentEntry(segment, roomId, tag)
    }
}

/**
 * `{"data": [{"iotRoomId", "robotRoomId", "robotTagId"}, ...], "need_retry": 1}` with the whole
 * table, sorted by segment like the official app sent it.
 */
internal fun encodeNameSegmentParams(table: List<SegmentEntry>): JsonObject = withRetry(nameSegmentData(table))

internal fun nameSegmentData(table: List<SegmentEntry>): JsonArray =
    JsonArray(table.sortedBy { it.segment }.map { e ->
        JsonObject(mapOf(
            "iotRoomId" to JsonPrimitive(e.roomId),
            "robotRoomId" to JsonPrimitive(e.segment),
            "robotTagId" to JsonPrimitive(e.tagId),
        ))
    })

/**
 * The final name_segment answer maps each segment to its id afterwards:
 * `[{"oldId": 16, "newId": 16}, ...]`. Returns old -> new; empty if the shape is unexpected.
 */
internal fun parseSegmentRenumbering(result: JsonElement?): Map<Int, Int> {
    val list = result as? JsonArray ?: return emptyMap()
    return list.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val old = o["oldId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
        val new = o["newId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
        old to new
    }.toMap()
}

suspend fun VacuumSession.startEditMap(): V1Response = sendCommand("start_edit_map").requireOk("start_edit_map")
suspend fun VacuumSession.endEditMap(): V1Response = sendCommand("end_edit_map").requireOk("end_edit_map")

/**
 * Run [block] between start_edit_map and end_edit_map, as the official app does around
 * save_map and floor type changes. end_edit_map is sent even when [block] fails.
 */
suspend fun <T> VacuumSession.inEditSession(block: suspend () -> T): T {
    startEditMap()
    try {
        return block()
    } finally {
        runCatching { endEditMap() }
    }
}

/** Replaces the robot's whole restriction set with exactly these walls and no-go zones. */
suspend fun VacuumSession.saveMap(noGoZones: List<MapZone>, walls: List<VirtualWall>, mapIndex: Int): V1Response =
    sendData("save_map", saveMapData(noGoZones, walls, mapIndex))

/**
 * `{"map_index": 0, "type": 1, "zone_data": [[8 coords], ...]}`. The list is the complete set of
 * carpet areas: adding one re-sent the existing one, deleting one sent the rest (captured).
 *
 * On a map that stores carpet flags (block 39) the official app added `"flags": [...]`, one per
 * carpet in zone_data order, re-sending existing carpets with the flags read from the map
 * (captured 2026-09-29). Carpets without flags are new ones and get the rectangle value.
 */
internal fun encodeCarpetAreaParams(carpets: List<MapZone>, mapIndex: Int, withFlags: Boolean): JsonObject {
    mapIndexBlocker(mapIndex)?.let { throw MapEditException(it) }
    return JsonObject(buildMap {
        if (withFlags) put("flags", JsonArray(carpets.map { JsonPrimitive(it.flags ?: NEW_RECT_CARPET_FLAGS) }))
        put("map_index", JsonPrimitive(mapIndex))
        put("type", JsonPrimitive(CARPET_AREA_TYPE))
        put("zone_data", JsonArray(carpets.map { JsonArray(quad(it)) }))
    })
}

/** The only carpet area type the official app sent. */
internal const val CARPET_AREA_TYPE = 1

/**
 * `{"map_index": 0, "sides_infos": [[0,0,0,0], ...], "zones": [[2, 8 coords], ...]}`, the
 * complete set of thresholds. The map stores only the corners, and the official app itself
 * re-sent existing thresholds as type 2 with sides [0,0,0,0], so every entry is written that way.
 */
internal fun encodeThresholdParams(thresholds: List<MapZone>, mapIndex: Int): JsonObject {
    mapIndexBlocker(mapIndex)?.let { throw MapEditException(it) }
    return JsonObject(mapOf(
        "map_index" to JsonPrimitive(mapIndex),
        "sides_infos" to JsonArray(thresholds.map { JsonArray(List(4) { JsonPrimitive(0) }) }),
        "zones" to JsonArray(thresholds.map { JsonArray(listOf(JsonPrimitive(THRESHOLD_TYPE)) + quad(it)) }),
    ))
}

internal const val THRESHOLD_TYPE = 2

/** Width of the threshold strips the official app drew (both captured ones measured ~114 mm). */
internal const val THRESHOLD_WIDTH_MM = 114

/**
 * A threshold strip along the line A-B, [THRESHOLD_WIDTH_MM] wide, with corners in the order the
 * official app used: A and B on one side, then B and A on the other.
 */
fun thresholdStrip(a: Pair<Int, Int>, b: Pair<Int, Int>): MapZone {
    val dx = (b.first - a.first).toDouble()
    val dy = (b.second - a.second).toDouble()
    val len = kotlin.math.sqrt(dx * dx + dy * dy)
    if (len == 0.0) throw MapEditException("Draw the threshold as a line across the doorway")
    val half = THRESHOLD_WIDTH_MM / 2.0
    val nx = -dy / len * half
    val ny = dx / len * half
    fun p(x: Int, y: Int, sign: Int) =
        kotlin.math.round(x + sign * nx).toInt() to kotlin.math.round(y + sign * ny).toInt()
    val a1 = p(a.first, a.second, 1); val b1 = p(b.first, b.second, 1)
    val b2 = p(b.first, b.second, -1); val a2 = p(a.first, a.second, -1)
    return MapZone(a1.first, a1.second, b1.first, b1.second, b2.first, b2.second, a2.first, a2.second, ZoneKind.THRESHOLD)
}

/** `{"data": [segmentA, segmentB], "need_retry": 1}` */
internal fun encodeMergeSegmentParams(segmentA: Int, segmentB: Int): JsonObject =
    withRetry(mergeSegmentData(segmentA, segmentB))

internal fun mergeSegmentData(segmentA: Int, segmentB: Int): JsonArray =
    JsonArray(listOf(JsonPrimitive(segmentA), JsonPrimitive(segmentB)))

/** The robot's answer when it can't divide a room along the given line. */
internal const val SPLIT_FAILED_CODE = -10006

/**
 * What the robot's `-10006 Split map failed` means in practice. It checks that the line cuts the
 * room in two, not how long the line is: on a Qrevo Pro a 1100 long cut across a narrow room worked
 * while a 950 long cut that stopped inside a wider room did not, and the official app was refused
 * the same way for a line that ended inside the room. A 5400 long line past both walls worked.
 */
internal const val SPLIT_REFUSED_MESSAGE =
    "The robot couldn't split the room along that line. Draw the line all the way across the room, " +
        "past the wall on both sides, so it cuts the room in two."

/** A refused split in words that say what to change; any other error is left as it was. */
internal fun splitFailure(e: RobotCommandException): Exception =
    if (e.code() == SPLIT_FAILED_CODE) MapEditException(SPLIT_REFUSED_MESSAGE) else e

/**
 * The two ways to send a split line, in the order to try them. In the capture the robot refused a
 * line (`-10006 Split map failed`) and accepted the same line with its ends swapped; the accepted
 * one started at the lower Y. So the lower-Y end goes first, and the reverse is the fallback.
 */
internal fun splitLineAttempts(a: Pair<Int, Int>, b: Pair<Int, Int>): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
    val aFirst = a.second < b.second || (a.second == b.second && a.first <= b.first)
    val first = if (aFirst) a to b else b to a
    return listOf(first, first.second to first.first)
}

fun RobotCommandException.code(): Int? =
    runCatching { error["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()

suspend fun VacuumSession.setCarpetAreas(carpets: List<MapZone>, mapIndex: Int, withFlags: Boolean): V1Response =
    sendCommandRaw("set_carpet_area", encodeCarpetAreaParams(carpets, mapIndex, withFlags), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("set_carpet_area")

suspend fun VacuumSession.setThresholds(thresholds: List<MapZone>, mapIndex: Int): V1Response =
    sendCommandRaw("app_set_smart_door_sill", encodeThresholdParams(thresholds, mapIndex), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("app_set_smart_door_sill")

suspend fun VacuumSession.mergeSegment(segmentA: Int, segmentB: Int): V1Response =
    sendData("merge_segment", mergeSegmentData(segmentA, segmentB))

suspend fun VacuumSession.splitSegment(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): V1Response =
    sendData("split_segment", splitSegmentData(segment, xA, yA, xB, yB))

suspend fun VacuumSession.setSegmentGroundMaterial(segment: Int, materialId: Int, direction: Int?): V1Response =
    sendCommandRaw("set_segment_ground_material", encodeGroundMaterialParams(segment, materialId, direction), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("set_segment_ground_material")

suspend fun VacuumSession.nameSegment(table: List<SegmentEntry>): V1Response =
    sendData("name_segment", nameSegmentData(table))

/*
 * Map management, from a capture of the official app on the S8 Pro Ultra (2026-09-29): rename,
 * delete, then map again. None used need_retry or start/end_edit_map, and each answered ["ok"]
 * within a second. After del_map the map list was empty, and app_start_build_map was sent only
 * then; the robot reported state 29 for the mapping run.
 */

/**
 * Only plain ASCII names for now: the captured `length` was 4 for "Test", which can't tell a
 * character count from a byte count, and the two differ for names like "Kælder".
 */
fun mapNameBlocker(name: String): String? = when {
    name.isBlank() -> "Give the map a name"
    name.any { it.code !in 0x20..0x7E } -> "Map names can only use plain letters, digits and punctuation for now (no æ, ø, å or emoji)"
    else -> null
}

/** `[{"length": 4, "multi_map": 0, "name": "Test"}]` */
internal fun encodeNameMultiMapParams(mapFlag: Int, name: String): List<JsonObject> {
    mapNameBlocker(name)?.let { throw MapEditException(it) }
    return listOf(JsonObject(mapOf(
        "length" to JsonPrimitive(name.length),
        "multi_map" to JsonPrimitive(mapFlag),
        "name" to JsonPrimitive(name),
    )))
}

suspend fun VacuumSession.nameMultiMap(mapFlag: Int, name: String): V1Response =
    sendCommand("name_multi_map", encodeNameMultiMapParams(mapFlag, name)).requireOk("name_multi_map")

/** `[mapFlag]`. Deletes the map with its rooms, names, zones, walls, carpets and thresholds. */
suspend fun VacuumSession.deleteMap(mapFlag: Int): V1Response =
    sendCommand("del_map", listOf(JsonPrimitive(mapFlag))).requireOk("del_map")

/** Starts a mapping run (the official app's quick mapping). Captured with 0 and with 2 saved maps. */
suspend fun VacuumSession.startBuildMap(): V1Response =
    sendCommand("app_start_build_map").requireOk("app_start_build_map")

/*
 * Multiple maps, from a second capture (2026-09-29, max_multi_map 4): the official app switched
 * maps with load_multi_map in the need_retry form, and saved a freshly built map with
 * manual_segment_map {"map_flag": -1} after stopping the mapping run. The robot filed it under the
 * next free mapFlag and answered [1] after ~7 s. The app followed each load with a
 * retry_request; the robot had already switched by then (map_status changed first), so it is not
 * sent here.
 */

/** `{"data": [mapFlag], "need_retry": 1}` */
internal fun encodeLoadMultiMapParams(mapFlag: Int): JsonObject = withRetry(loadMultiMapData(mapFlag))

internal fun loadMultiMapData(mapFlag: Int): JsonArray = JsonArray(listOf(JsonPrimitive(mapFlag)))

suspend fun VacuumSession.loadMultiMap(mapFlag: Int): V1Response =
    sendData("load_multi_map", loadMultiMapData(mapFlag))

/** map_flag -1: store the map just built as a new map. */
internal const val NEW_MAP_FLAG = -1

/** `[{"map_flag": -1}]` */
internal fun encodeSaveNewMapParams(): List<JsonObject> =
    listOf(JsonObject(mapOf("map_flag" to JsonPrimitive(NEW_MAP_FLAG))))

suspend fun VacuumSession.saveNewMap(): V1Response =
    sendCommand("manual_segment_map", encodeSaveNewMapParams(), timeoutMs = SAVE_NEW_MAP_TIMEOUT_MS)
        .requireOk("manual_segment_map")

/** The captured manual_segment_map answer took 7 s; the robot splits the new map into rooms. */
private const val SAVE_NEW_MAP_TIMEOUT_MS = 60_000L

/*
 * Floor settings, from a third capture (2026-09-29 17:28-17:33). Turning multi-level maps off sent
 * set_lab_status with the map to keep as reserve_map; the robot deleted every other map. Turning
 * it back on sent lab_status 3 alone, then set_switch_map_mode {"mode": 1}. Status reports both
 * as lab_status and switch_map_mode.
 *
 * A robot that never met the official app reports lab_status 0 and refuses manual_segment_map
 * with "The Lab Status is disable" (S8 on 02.33.88, issue #17). A Qrevo S5V on the same firmware
 * took the plain `[{"lab_status": 1}]` (local_roborock_server issue #148).
 */

/** `{"data": [{"lab_status": 1, "reserve_map": 0}], "need_retry": 1}`, or without reserve_map when turning it on. */
internal fun encodeSetLabStatusParams(labStatus: Int, reserveMap: Int?): JsonObject =
    withRetry(labStatusData(labStatus, reserveMap))

internal fun labStatusData(labStatus: Int, reserveMap: Int?): JsonArray =
    JsonArray(listOf(JsonObject(buildMap {
        put("lab_status", JsonPrimitive(labStatus))
        reserveMap?.let { put("reserve_map", JsonPrimitive(it)) }
    })))

/**
 * The `map_info` entries of get_multi_maps_list. An unnamed map must still be listed: saving zones
 * relies on the count being right. Each map lists its on-robot backup under `bak_maps`.
 */
internal fun parseFloorMapList(mapInfo: JsonArray): List<FloorMap> = mapInfo.mapNotNull { elem ->
    val o = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
    val flag = o["mapFlag"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
    val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Map ${flag + 1}"
    val backup = runCatching { o["bak_maps"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
    FloorMap(
        name = name,
        mapFlag = flag,
        savedAt = o["add_time"]?.jsonPrimitive?.longOrNull,
        backupAt = backup?.get("add_time")?.jsonPrimitive?.longOrNull,
    )
}

/*
 * On-robot map backups, from captures of the official app on the S8 Pro Ultra (2026-10-28, with
 * multi-level maps on and off). The robot keeps one backup per saved map (max_bak_map 1), listed
 * under the map as bak_maps. Backing up replaces it. Restoring put the map back as it was at backup
 * time: a no-go zone added after the backup was gone again, a wall in the backup stayed. Neither
 * used need_retry or start/end_edit_map. The older get_recover_maps answers unknown_method on the
 * S8 and on the Qrevo Curv 2 Flow.
 */

/** `{"map_flag": 0}`, the form manual_bak_map and recover_multi_map were captured with. */
internal fun encodeMapFlagParams(mapFlag: Int): JsonObject = JsonObject(mapOf("map_flag" to JsonPrimitive(mapFlag)))

/** Replaces [mapFlag]'s backup with the map as it is now. Answered ["ok"] within 2 s. */
suspend fun VacuumSession.backupMap(mapFlag: Int): V1Response =
    sendCommandRaw("manual_bak_map", encodeMapFlagParams(mapFlag), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("manual_bak_map")

/** Puts [mapFlag] back to its backup. Answered ["ok"] after about 2.5 s, map_status passing 19. */
suspend fun VacuumSession.restoreMapBackup(mapFlag: Int): V1Response =
    sendCommandRaw("recover_multi_map", encodeMapFlagParams(mapFlag), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("recover_multi_map")

suspend fun VacuumSession.setLabStatus(labStatus: Int, reserveMap: Int?): V1Response =
    sendData("set_lab_status", labStatusData(labStatus, reserveMap))

/** `{"mode": 0}` recognise the floor automatically, `{"mode": 1}` pick it by hand. */
internal fun encodeSwitchMapModeParams(mode: Int): JsonObject = JsonObject(mapOf("mode" to JsonPrimitive(mode)))

suspend fun VacuumSession.setSwitchMapMode(mode: Int): V1Response =
    sendCommandRaw("set_switch_map_mode", encodeSwitchMapModeParams(mode)).requireOk("set_switch_map_mode")

/** Map edits make the robot rewrite its map; the captured final answers took 2-4 s. */
private const val MAP_EDIT_TIMEOUT_MS = 20_000L
