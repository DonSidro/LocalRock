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
 * Trailing entry the official app always appends to save_map, observed as `[100, 0]` on a robot
 * whose map list held a single map with mapFlag 0. Sent only in that captured situation.
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
 * Every captured map edit named map index 0 (save_map's `[100, 0]`, and `"map_index": 0` on
 * carpets and thresholds) on a robot with one saved map. Anything else is not confirmed.
 */
fun mapIndexBlocker(mapIndex: Int?): String? = when (mapIndex) {
    null -> "Saving map edits is only supported on robots with a single saved map for now."
    0 -> null
    else -> "Saving map edits is only confirmed for map 0. This robot's map is $mapIndex."
}

fun restrictionVertexCount(walls: Int, zones: Int): Int = walls * 2 + zones * 4

private fun coord(v: Int): JsonPrimitive {
    if (v !in 0..MAX_MAP_COORD_MM) throw MapEditException("Coordinate $v mm is outside the map")
    return JsonPrimitive(v)
}

private fun quad(z: MapZone): List<JsonPrimitive> =
    listOf(coord(z.x0), coord(z.y0), coord(z.x1), coord(z.y1), coord(z.x2), coord(z.y2), coord(z.x3), coord(z.y3))

private fun withRetry(data: JsonElement) = JsonObject(mapOf("data" to data, "need_retry" to JsonPrimitive(1)))

/**
 * `{"data": [walls..., no-go zones..., [100, mapIndex]], "need_retry": 1}`, the order the official
 * app used. Zone corners are written in the order they were read so an untouched zone round-trips.
 */
internal fun encodeSaveMapParams(noGoZones: List<MapZone>, walls: List<VirtualWall>, mapIndex: Int): JsonObject {
    if (mapIndex != 0) throw MapEditException("Saving zones is only confirmed for map index 0")
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
    return withRetry(JsonArray(entries))
}

/** `{"data": [segment, xA, yA, xB, yB], "need_retry": 1}` */
internal fun encodeSplitSegmentParams(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): JsonObject =
    withRetry(JsonArray(listOf(JsonPrimitive(segment), coord(xA), coord(yA), coord(xB), coord(yB))))

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
 * `{"data": [{"iotRoomId", "robotRoomId", "robotTagId"}, ...], "need_retry": 1}` with the whole
 * table, sorted by segment like the official app sent it.
 */
internal fun encodeNameSegmentParams(table: List<SegmentEntry>): JsonObject =
    withRetry(JsonArray(table.sortedBy { it.segment }.map { e ->
        JsonObject(mapOf(
            "iotRoomId" to JsonPrimitive(e.roomId),
            "robotRoomId" to JsonPrimitive(e.segment),
            "robotTagId" to JsonPrimitive(e.tagId),
        ))
    }))

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
    sendCommandRaw("save_map", encodeSaveMapParams(noGoZones, walls, mapIndex), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("save_map")

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
    withRetry(JsonArray(listOf(JsonPrimitive(segmentA), JsonPrimitive(segmentB))))

/** The robot's answer when it can't divide a room along the given line. */
internal const val SPLIT_FAILED_CODE = -10006

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
    sendCommandRaw("merge_segment", encodeMergeSegmentParams(segmentA, segmentB), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("merge_segment")

suspend fun VacuumSession.splitSegment(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): V1Response =
    sendCommandRaw("split_segment", encodeSplitSegmentParams(segment, xA, yA, xB, yB), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("split_segment")

suspend fun VacuumSession.setSegmentGroundMaterial(segment: Int, materialId: Int, direction: Int?): V1Response =
    sendCommandRaw("set_segment_ground_material", encodeGroundMaterialParams(segment, materialId, direction), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("set_segment_ground_material")

suspend fun VacuumSession.nameSegment(table: List<SegmentEntry>): V1Response =
    sendCommandRaw("name_segment", encodeNameSegmentParams(table), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("name_segment")

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
internal fun encodeLoadMultiMapParams(mapFlag: Int): JsonObject = withRetry(JsonArray(listOf(JsonPrimitive(mapFlag))))

suspend fun VacuumSession.loadMultiMap(mapFlag: Int): V1Response =
    sendCommandRaw("load_multi_map", encodeLoadMultiMapParams(mapFlag), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("load_multi_map")

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
 */

/** `{"data": [{"lab_status": 1, "reserve_map": 0}], "need_retry": 1}`, or without reserve_map when turning it on. */
internal fun encodeSetLabStatusParams(labStatus: Int, reserveMap: Int?): JsonObject =
    withRetry(JsonArray(listOf(JsonObject(buildMap {
        put("lab_status", JsonPrimitive(labStatus))
        reserveMap?.let { put("reserve_map", JsonPrimitive(it)) }
    }))))

suspend fun VacuumSession.setLabStatus(labStatus: Int, reserveMap: Int?): V1Response =
    sendCommandRaw("set_lab_status", encodeSetLabStatusParams(labStatus, reserveMap), timeoutMs = MAP_EDIT_TIMEOUT_MS)
        .requireOk("set_lab_status")

/** `{"mode": 0}` recognise the floor automatically, `{"mode": 1}` pick it by hand. */
internal fun encodeSwitchMapModeParams(mode: Int): JsonObject = JsonObject(mapOf("mode" to JsonPrimitive(mode)))

suspend fun VacuumSession.setSwitchMapMode(mode: Int): V1Response =
    sendCommandRaw("set_switch_map_mode", encodeSwitchMapModeParams(mode)).requireOk("set_switch_map_mode")

/** Map edits make the robot rewrite its map; the captured final answers took 2-4 s. */
private const val MAP_EDIT_TIMEOUT_MS = 20_000L
