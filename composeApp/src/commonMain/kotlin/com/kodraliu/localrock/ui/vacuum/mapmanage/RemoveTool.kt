package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import com.kodraliu.localrock.shared.vacuum.mapIndexBlocker
import com.kodraliu.localrock.shared.vacuum.requireOk
import com.kodraliu.localrock.shared.vacuum.saveMapIndex
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Remove tool (NewFeatureStrBit.MapEraser, 71). Wire shape from the official app's traffic:
// app_set_beautify_blocks {"map_index": n, "data": [[id, x1,y1, x2,y1, x2,y2, x1,y2], ...]}, which
// replaces the whole list; an empty data list clears every removal area. Track B6 may replace this builder.

internal const val BEAUTIFY_BLOCKS_METHOD = "app_set_beautify_blocks"

/** Most removal areas one map takes. The plugin's own number was not found; inferred from its other zone limits. */
internal const val MAX_ERASER_ZONES = 10

/** How near the dock a removal area may come, in mm. Inferred: the plugin's own distance was not found. */
internal const val ERASER_DOCK_CLEARANCE_MM = 300

/** The full removal list for one map: each area's bounds as top-left, top-right, bottom-right, bottom-left. */
internal fun beautifyBlocksParams(mapIndex: Int, zones: List<MapZone>): JsonObject = JsonObject(
    mapOf(
        "map_index" to JsonPrimitive(mapIndex),
        "data" to JsonArray(
            zones.mapIndexed { id, z ->
                JsonArray(
                    listOf(id, z.minXmm, z.maxYmm, z.maxXmm, z.maxYmm, z.maxXmm, z.minYmm, z.minXmm, z.minYmm)
                        .map { JsonPrimitive(it) },
                )
            },
        ),
    ),
)

/** True when any area covers the dock or comes within [ERASER_DOCK_CLEARANCE_MM] of it. */
internal fun eraserTooCloseToDock(zones: List<MapZone>, dock: ParsedMapPoint?): Boolean {
    dock ?: return false
    return zones.any {
        it.kind == ZoneKind.ERASER &&
            dock.x in (it.minXmm - ERASER_DOCK_CLEARANCE_MM)..(it.maxXmm + ERASER_DOCK_CLEARANCE_MM) &&
            dock.y in (it.minYmm - ERASER_DOCK_CLEARANCE_MM)..(it.maxYmm + ERASER_DOCK_CLEARANCE_MM)
    }
}

/** The robot stores what was sent when its map's removal areas have the same bounds, in any order. */
internal fun eraserZonesMatch(sent: List<MapZone>, onMap: List<MapZone>): Boolean {
    fun key(z: MapZone) = listOf(z.minXmm, z.minYmm, z.maxXmm, z.maxYmm)
    // The robot may round a corner by a millimetre or two.
    val a = sent.map(::key).sortedWith(compareBy({ it[0] }, { it[1] }))
    val b = onMap.map(::key).sortedWith(compareBy({ it[0] }, { it[1] }))
    return a.size == b.size && a.zip(b).all { (x, y) -> x.zip(y).all { (p, q) -> kotlin.math.abs(p - q) <= 2 } }
}

private const val ERASER_READBACK_ATTEMPTS = 6
private const val ERASER_NOT_CONFIRMED =
    "The robot accepted the change, but the map does not show it yet. Refresh the map in a moment to check."
private const val ERASER_READBACK_DELAY_MS = 2_000L

/** Send the full removal list for the robot's only map, then wait for a fresh map to show it. */
internal suspend fun saveEraserZones(repository: VacuumRepository, zones: List<MapZone>) {
    repository.editBlockedReason()?.let { throw MapEditException(it) }
    runCatching { repository.fetchFloorMaps() }
    if (!repository.floorMapsLoaded.value) {
        throw MapEditException("Couldn't read the robot's saved maps, so nothing was saved. Try again.")
    }
    val mapIndex = saveMapIndex(repository.floorMaps.value)
    mapIndexBlocker(mapIndex)?.let { throw MapEditException(it) }
    if (zones.size > MAX_ERASER_ZONES) throw MapEditException(com.kodraliu.localrock.ui.strings.MapStrings.REMOVE_LIMIT)
    repository.session.sendCommandRaw(BEAUTIFY_BLOCKS_METHOD, beautifyBlocksParams(mapIndex!!, zones), timeoutMs = 15_000L)
        .requireOk(BEAUTIFY_BLOCKS_METHOD)
    repeat(ERASER_READBACK_ATTEMPTS) { attempt ->
        if (attempt > 0) delay(ERASER_READBACK_DELAY_MS)
        runCatching { repository.fetchMap() }
        val map = repository.parsedMap.value
        if (map != null && eraserZonesMatch(zones, map.eraserZones)) return
    }
    throw MapEditException(ERASER_NOT_CONFIRMED)
}
