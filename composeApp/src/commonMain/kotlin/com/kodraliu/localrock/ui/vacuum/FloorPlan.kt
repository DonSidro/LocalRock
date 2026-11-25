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
// Type 6 is where two rooms touch with no type 2 edge on either side (the open boundary on a real Curv map); it is drawn as an inner wall.
internal val FLOOR_PLAN_INNER = setOf(2, 6)

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
 * The cells' and floor-plan polygons' bounds, grown by half a stroke of [strokeCells] and kept inside the image.
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

/** Pairs of room ids (smaller first) whose polygons share a stretch of wall, as the plugin's getNeighboursInfo finds them from the zone lines. */
internal fun floorPlanTouches(zones: List<FloorPlanZone>): Set<Pair<Int, Int>> {
    // Axis-aligned edges grouped by the line they lie on: (vertical = true, coordinate) -> (low, high, room).
    val lines = HashMap<Pair<Boolean, Int>, MutableList<Triple<Int, Int, Int>>>()
    for (zone in zones) {
        val pts = zone.points
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            when {
                a.x == b.x && a.y != b.y -> lines.getOrPut(true to a.x) { mutableListOf() }.add(Triple(minOf(a.y, b.y), maxOf(a.y, b.y), zone.segment))
                a.y == b.y && a.x != b.x -> lines.getOrPut(false to a.y) { mutableListOf() }.add(Triple(minOf(a.x, b.x), maxOf(a.x, b.x), zone.segment))
            }
        }
    }
    val touches = HashSet<Pair<Int, Int>>()
    for (edges in lines.values) {
        for (i in edges.indices) for (j in i + 1 until edges.size) {
            val (lo1, hi1, r1) = edges[i]
            val (lo2, hi2, r2) = edges[j]
            if (r1 != r2 && minOf(hi1, hi2) > maxOf(lo1, lo2)) touches += minOf(r1, r2) to maxOf(r1, r2)
        }
    }
    return touches
}


/** A room outline may cover at most this share of cells that are not scanned floor or wall. */
internal const val FLOOR_PLAN_MAX_SPILL = 0.3f

/** Outlines smaller than this many cells are not judged: a few edge cells would swing the share. */
internal const val FLOOR_PLAN_MIN_JUDGED_CELLS = 64

/**
 * Whether the robot's room outlines fit its scanned floor. Some robots (a Qrevo Curv 2 Flow) send an
 * outline that is a plain box around the room, and painting from it floods the room colour far past
 * its walls. When any sizeable outline spills more than [maxSpill] of its area onto cells that are not
 * floor (128) or wall (127), the map is drawn from its cells instead, as before floor plans were used.
 * [grid] is row-major from the bottom row, as in ParsedMap; a cell (x, y) is drawn at x..x+1, h-1-y..h-y.
 */
internal fun floorPlanFitsGrid(
    shapes: List<FloorPlanShape>, grid: ByteArray, width: Int, height: Int,
    maxSpill: Float = FLOOR_PLAN_MAX_SPILL,
): Boolean {
    for (shape in shapes) {
        val pts = shape.fill
        if (pts.size < 3) continue
        var inside = 0
        var spill = 0
        val crossings = ArrayList<Float>()
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        // Drawn row centre yc = h - y - 0.5, so the rows the outline can reach are these.
        val yFrom = (height - maxY - 0.5f).toInt().coerceIn(0, height - 1)
        val yTo = (height - minY - 0.5f).toInt().coerceIn(0, height - 1)
        for (y in yFrom..yTo) {
            val yc = height - y - 0.5f
            crossings.clear()
            for (i in pts.indices) {
                val a = pts[i]
                val b = pts[(i + 1) % pts.size]
                if ((a.y > yc) != (b.y > yc)) crossings += a.x + (yc - a.y) * (b.x - a.x) / (b.y - a.y)
            }
            crossings.sort()
            // Even-odd spans, the same rule a path fill uses; a cell counts when its centre is inside.
            var k = 0
            while (k + 1 < crossings.size) {
                val x0 = kotlin.math.ceil(crossings[k] - 0.5f).toInt().coerceAtLeast(0)
                val x1 = (kotlin.math.ceil(crossings[k + 1] - 0.5f).toInt() - 1).coerceAtMost(width - 1)
                for (x in x0..x1) {
                    inside++
                    val gv = grid[y * width + x].toInt() and 0xff
                    if (gv != 127 && gv != 128) spill++
                }
                k += 2
            }
        }
        if (inside >= FLOOR_PLAN_MIN_JUDGED_CELLS && spill > inside * maxSpill) {
            println("[VacLocal] floor plan: room ${shape.segment} outline is ${spill * 100 / inside}% off the scanned floor ($spill of $inside cells); drawing cells")
            return false
        }
    }
    return true
}
