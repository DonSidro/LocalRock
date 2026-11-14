package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanPoint
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanZone

/** Image-frame cell corners to drawn coordinates: x is the column, y is flipped like the floor so row 0 is at the bottom. */
internal class FloorPlanFrame(val height: Int) {
    fun offsetOf(p: FloorPlanPoint) = Offset(p.x.toFloat(), (height - p.y).toFloat())
}

/** One straight stretch of a wall, door or edge in drawn coordinates. */
internal data class FloorPlanRun(val from: Offset, val to: Offset)

/** A room's closed [fill] outline plus its edges, merged into straight runs, by the type of their starting point. */
internal data class FloorPlanShape(
    val segment: Int,
    val fill: List<Offset>,
    val outer: List<FloorPlanRun>,
    val inner: List<FloorPlanRun>,
    val door: List<FloorPlanRun>,
)

internal val FLOOR_PLAN_OUTER = setOf(1)
internal val FLOOR_PLAN_INNER = setOf(2)

// The plugin names only 3 as a door, but on a real Curv map the edges of type 4 sit exactly where the official app draws its white door bars.
internal val FLOOR_PLAN_DOOR = setOf(3, 4)

/** Edge i runs from point i to point i + 1 (the last to the first) and takes point i's type; other types are not drawn. */
internal fun buildFloorPlan(zones: List<FloorPlanZone>, frame: FloorPlanFrame): List<FloorPlanShape> =
    zones.filter { it.points.size >= 3 }.map { zone ->
        val pts = zone.points.map(frame::offsetOf)
        fun runsOf(types: Set<Int>): List<FloorPlanRun> {
            val edges = ArrayList<FloorPlanRun?>(pts.size)
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                edges += if (zone.points[i].type in types && a != b) FloorPlanRun(a, b) else null
            }
            return mergeCollinear(edges)
        }
        FloorPlanShape(zone.segment, pts, runsOf(FLOOR_PLAN_OUTER), runsOf(FLOOR_PLAN_INNER), runsOf(FLOOR_PLAN_DOOR))
    }

/** Joins neighbouring edges that continue in a straight line; a null entry (an edge of another type) breaks a run, and the ring closes up. */
private fun mergeCollinear(edges: List<FloorPlanRun?>): List<FloorPlanRun> {
    val runs = ArrayList<FloorPlanRun>()
    var open: FloorPlanRun? = null
    for (edge in edges) {
        val cur = open
        open = when {
            edge == null -> { if (cur != null) runs += cur; null }
            cur != null && continues(cur, edge) -> FloorPlanRun(cur.from, edge.to)
            else -> { if (cur != null) runs += cur; edge }
        }
    }
    open?.let { runs += it }
    if (runs.size >= 2 && edges.first() != null && edges.last() != null && continues(runs.last(), runs.first())) {
        val first = runs.removeAt(0)
        runs[runs.size - 1] = FloorPlanRun(runs.last().from, first.to)
    }
    return runs
}

private fun continues(a: FloorPlanRun, b: FloorPlanRun): Boolean {
    if (a.to != b.from) return false
    val ax = a.to.x - a.from.x
    val ay = a.to.y - a.from.y
    val bx = b.to.x - b.from.x
    val by = b.to.y - b.from.y
    return ax * by - ay * bx == 0f && ax * bx + ay * by > 0f
}

/**
 * The union of the cells' bounds (null if none) and the floor-plan polygons' bounds, grown by half a stroke of
 * [strokeCells] and kept inside the image, in the same normalised drawn space as [MapContentBounds].
 */
internal fun floorPlanBoundsNorm(
    cells: MapContentBounds?,
    zones: List<FloorPlanZone>,
    width: Int,
    height: Int,
    strokeCells: Float,
): MapContentBounds {
    val pts = zones.flatMap { it.points }
    if (pts.isEmpty()) return cells ?: MapContentBounds(0f, 0f, 1f, 1f)
    val half = strokeCells / 2f
    var left = (pts.minOf { it.x } - half) / width
    var right = (pts.maxOf { it.x } + half) / width
    var top = (height - pts.maxOf { it.y } - half) / height
    var bottom = (height - pts.minOf { it.y } + half) / height
    if (cells != null) {
        left = minOf(left, cells.left); right = maxOf(right, cells.right)
        top = minOf(top, cells.top); bottom = maxOf(bottom, cells.bottom)
    }
    return MapContentBounds(left.coerceAtLeast(0f), top.coerceAtLeast(0f), right.coerceAtMost(1f), bottom.coerceAtMost(1f))
}
