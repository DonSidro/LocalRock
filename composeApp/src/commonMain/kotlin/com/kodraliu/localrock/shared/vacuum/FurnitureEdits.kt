package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.map.FurnitureKind
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapFurniture
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.furnitureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Positive keys are stable robot IDs; negative keys exist only in an unsaved local draft. */
data class FurniturePlacement(val key: Int, val type: Int, val subtype: Int, val corners: List<ParsedMapPoint>) {
    val kind: FurnitureKind? get() = furnitureKind(type, subtype)
    val widthMm: Double get() = distance(corners[0], corners[1])
    val depthMm: Double get() = distance(corners[0], corners[3])
    val angleDegrees: Double get() = atan2((corners[1].y - corners[0].y).toDouble(),
        (corners[1].x - corners[0].x).toDouble()) * 180 / kotlin.math.PI

    fun moved(dx: Int, dy: Int): FurniturePlacement = copy(corners = corners.map { ParsedMapPoint(it.x + dx, it.y + dy) })

    fun rotated(degrees: Double): FurniturePlacement {
        val cx = corners.sumOf { it.x }.toDouble() / 4
        val cy = corners.sumOf { it.y }.toDouble() / 4
        val radians = degrees * kotlin.math.PI / 180
        return copy(corners = corners.map { p ->
            val x = p.x - cx; val y = p.y - cy
            ParsedMapPoint((cx + x * cos(radians) - y * sin(radians)).roundToInt(),
                (cy + x * sin(radians) + y * cos(radians)).roundToInt())
        })
    }

    fun resized(width: Double, depth: Double): FurniturePlacement {
        val cx = corners.sumOf { it.x }.toDouble() / 4
        val cy = corners.sumOf { it.y }.toDouble() / 4
        val ux = (corners[1].x - corners[0].x) / widthMm
        val uy = (corners[1].y - corners[0].y) / widthMm
        val vx = (corners[3].x - corners[0].x) / depthMm
        val vy = (corners[3].y - corners[0].y) / depthMm
        return copy(corners = listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (x, y) ->
            ParsedMapPoint((cx + x * ux * width / 2 + y * vx * depth / 2).roundToInt(),
                (cy + x * uy * width / 2 + y * vy * depth / 2).roundToInt())
        })
    }

    fun contains(x: Int, y: Int): Boolean {
        val turns = corners.indices.map { i ->
            val a = corners[i]; val b = corners[(i + 1) % 4]
            (b.x - a.x).toLong() * (y - a.y) - (b.y - a.y).toLong() * (x - a.x)
        }
        return turns.all { it >= 0 } || turns.all { it <= 0 }
    }
}

private fun distance(a: ParsedMapPoint, b: ParsedMapPoint) = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())

fun newFurniture(key: Int, kind: FurnitureKind, x: Int, y: Int): FurniturePlacement {
    require(key < 0)
    // Robot Y grows upwards: top corners have the larger Y, as in the map renderer.
    val left = x - kind.widthMm / 2; val top = y + kind.depthMm / 2
    return FurniturePlacement(key, kind.type, kind.subtype, listOf(
        ParsedMapPoint(left, top), ParsedMapPoint(left + kind.widthMm, top),
        ParsedMapPoint(left + kind.widthMm, top - kind.depthMm), ParsedMapPoint(left, top - kind.depthMm),
    ))
}

/** Generic editor geometry limits, not OEM size limits. Preserve untouched records verbatim. */
fun validFurniturePlacement(item: FurniturePlacement): Boolean {
    if (item.key == 0 || item.kind == null || item.corners.size != 4) return false
    if (item.corners.any { it.x !in 0..MAX_MAP_COORD_MM || it.y !in 0..MAX_MAP_COORD_MM }) return false
    if (item.widthMm < 100 || item.depthMm < 100) return false
    val a = item.corners[0]; val b = item.corners[1]; val c = item.corners[2]; val d = item.corners[3]
    if (abs(a.x + c.x - b.x - d.x) > 3 || abs(a.y + c.y - b.y - d.y) > 3) return false
    val dot = (b.x - a.x).toDouble() * (d.x - a.x) + (b.y - a.y).toDouble() * (d.y - a.y)
    return abs(dot) <= item.widthMm * item.depthMm * .025
}

fun MapFurniture.editablePlacement(): FurniturePlacement? =
    if (!isVisible || id !in 1..255) null
    else FurniturePlacement(id, type, subtype, cornersMm).takeIf(::validFurniturePlacement)

data class FurnitureSnapshot(val map: ParsedMap, val support: FurnitureSupport = FurnitureSupport.UNKNOWN) {
    val placements: List<FurniturePlacement> get() = map.furniture.mapNotNull { it.editablePlacement() }
}

fun furnitureMapBlocker(map: ParsedMap, mapIndex: Int?): String? = when {
    // save_furnitures still uses map_flag=0; the general map editor supports more floors.
    mapIndex != 0 -> "Furniture editing is available only on the first saved map."
    map.format != MapFormat.LEGACY -> "Furniture editing is not available for this map format yet."
    map.furnitureBlocks.any { !it.hasKnownLayout } -> "This map uses a furniture layout LocalRock cannot edit yet."
    map.furniture.any { it.id !in 1..255 } || map.furniture.map { it.id }.distinct().size != map.furniture.size ->
        "The robot's furniture identifiers are unclear. Refresh the map before editing."
    else -> null
}

/** Also catch a floor/room change while a draft is open; moving robot/path data is irrelevant. */
internal fun sameFurnitureBase(a: ParsedMap, b: ParsedMap): Boolean =
    a.format == b.format && a.width == b.width && a.height == b.height &&
        a.pixelOffsetLeft == b.pixelOffsetLeft && a.pixelOffsetTop == b.pixelOffsetTop &&
        a.grid.contentEquals(b.grid) && a.rooms.map { it.id }.toSet() == b.rooms.map { it.id }.toSet() &&
        a.furnitureBlocks == b.furnitureBlocks

internal data class FurnitureEditPlan(
    val baseline: FurnitureSnapshot,
    val draft: List<FurniturePlacement>,
    val changed: List<FurniturePlacement>,
    val deleted: Set<Int>,
) {
    val isEmpty: Boolean get() = changed.isEmpty() && deleted.isEmpty()
    fun params(): JsonObject = JsonObject(mapOf(
        "data" to JsonArray(changed.map { item ->
            JsonArray((listOf(1, if (item.key < 0) -1 else item.key) +
                item.corners.flatMap { listOf(it.x, it.y) } + listOf(item.type, item.subtype, 1)).map(::JsonPrimitive))
        } + deleted.sorted().map { JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(it))) }),
        "map_flag" to JsonPrimitive(0),
    ))
}

internal fun furnitureEditPlan(baseline: FurnitureSnapshot, draft: List<FurniturePlacement>): FurnitureEditPlan {
    baseline.support.blockedReason?.let { throw MapEditException(it) }
    furnitureMapBlocker(baseline.map, 0)?.let { throw MapEditException(it) }
    val original = baseline.placements.associateBy { it.key }
    if (draft.map { it.key }.distinct().size != draft.size || draft.any { !validFurniturePlacement(it) })
        throw MapEditException("One furniture item has an invalid size or position.")
    draft.filter { it.key > 0 }.forEach { item ->
        val old = original[item.key]
        if (old == null || old.type != item.type || old.subtype != item.subtype)
            throw MapEditException("The furniture selection has changed. Reload the editor.")
    }
    return FurnitureEditPlan(baseline, draft.toList(), draft.filter { original[it.key] != it },
        original.keys - draft.map { it.key }.toSet())
}

private fun matchesPlacement(expected: FurniturePlacement, actual: MapFurniture): Boolean {
    if (!actual.isVisible || expected.type != actual.type || expected.subtype != actual.subtype) return false
    // The map may cyclically reorder corners; each corner must still match a distinct corner.
    val points = actual.cornersMm
    return listOf(points, points.reversed()).any { order ->
        (0..3).any { shift -> expected.corners.indices.all { i ->
            val a = expected.corners[i]; val b = order[(i + shift) % 4]
            abs(a.x - b.x) <= READBACK_TOLERANCE_MM && abs(a.y - b.y) <= READBACK_TOLERANCE_MM
        } }
    }
}

internal fun furnitureEditsConfirmed(plan: FurnitureEditPlan, map: ParsedMap): Boolean {
    if (furnitureMapBlocker(map, 0) != null) return false
    val actual = map.furniture.associateBy { it.id }.toMutableMap()
    val updates = plan.changed.filter { it.key > 0 }.associateBy { it.key }
    for (original in plan.baseline.map.furniture) {
        if (original.id in plan.deleted) continue
        val saved = actual.remove(original.id) ?: return false
        val updated = updates[original.id]
        if (updated == null) {
            // Includes every hidden/unknown record and every untouched visible item.
            if (saved.rawBytes != original.rawBytes) return false
        } else if ((saved.cornersMm == original.cornersMm && updated.corners != original.cornersMm) ||
            !matchesPlacement(updated, saved) ||
            saved.rawBytes.subList(16, 18) != original.rawBytes.subList(16, 18) ||
            saved.rawBytes[22] != original.rawBytes[22]) return false
    }
    val additions = plan.changed.filter { it.key < 0 }
    if (actual.size != additions.size) return false
    // Bipartite matching handles overlapping additions without an exponential search.
    val candidates = actual.values.toList()
    val assigned = IntArray(candidates.size) { -1 }
    fun assign(item: Int, seen: BooleanArray): Boolean {
        for (i in candidates.indices) {
            if (seen[i] || !matchesPlacement(additions[item], candidates[i])) continue
            seen[i] = true
            if (assigned[i] < 0 || assign(assigned[i], seen)) {
                assigned[i] = item
                return true
            }
        }
        return false
    }
    return additions.indices.all { assign(it, BooleanArray(candidates.size)) }
}

private fun V1Response.requireFurnitureAck(method: String) {
    error?.let { throw RobotCommandException(method, it) }
    if (result != JsonArray(listOf(JsonPrimitive("ok"))))
        throw MapEditException("The robot did not confirm the furniture edit. Reload the map before trying again.")
}

/** No retry of a write: an acknowledgement can be lost after the robot applies the operation. */
internal suspend fun commitFurnitureEdits(
    baseline: FurnitureSnapshot,
    draft: List<FurniturePlacement>,
    readFresh: suspend () -> FurnitureSnapshot,
    send: suspend (String, JsonElement) -> V1Response,
    readbackDelay: suspend () -> Unit = { delay(2_000) },
): FurnitureSnapshot {
    val plan = furnitureEditPlan(baseline, draft)
    val current = readFresh()
    current.support.blockedReason?.let { throw MapEditException(it) }
    if (!sameFurnitureBase(baseline.map, current.map))
        throw MapEditException("The robot's map changed while you were editing. Reload it to start from the latest furniture.")
    if (plan.isEmpty) return current
    val empty = JsonArray(emptyList())
    var writeFailure: Throwable? = null
    var endFailure: Throwable? = null
    var mayHaveStarted = true
    try {
        val started = send("start_edit_map", empty)
        if (started.error != null) mayHaveStarted = false
        started.requireFurnitureAck("start_edit_map")
        send("save_furnitures", plan.params()).requireFurnitureAck("save_furnitures")
    } catch (e: Throwable) {
        writeFailure = e
    } finally {
        if (mayHaveStarted) withContext(NonCancellable) {
            try { withTimeout(15_000) { send("end_edit_map", empty).requireFurnitureAck("end_edit_map") } }
            catch (e: Throwable) { endFailure = e }
        }
    }
    writeFailure?.let { throw it }
    endFailure?.let { throw MapEditException("Furniture may have been saved, but the edit session could not be closed. Reload the map before editing again.") }
    repeat(4) { attempt ->
        if (attempt > 0) readbackDelay()
        val fresh = try { readFresh() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (fresh != null && furnitureEditsConfirmed(plan, fresh.map)) return fresh
    }
    throw MapEditException("The robot accepted the edit, but its map has not confirmed all changes. Reload before making another edit.")
}
