package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.MAX_MAP_COORD_MM
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import kotlin.math.hypot
import kotlin.math.roundToInt

/*
 * One definition of the map's coordinate spaces, shared by the renderer, the editor and every
 * gesture handler so a zone drawn on screen lands exactly where the robot will put it.
 *
 *   robot mm   what the robot sends and accepts; Y grows upwards; 50 mm per map pixel
 *   cell       bitmap pixel space of the rendered map; Y grows downwards (the renderer flips rows)
 *   canvas     cell space scaled to the size the bitmap is drawn at
 */

internal const val MM_PER_CELL = 50f

/** Robot mm -> bitmap cell (Y flipped to match the renderer). */
internal fun ParsedMap.mmToCell(xMm: Int, yMm: Int): Offset {
    val cellX = xMm / MM_PER_CELL - pixelOffsetLeft
    val cellYFromBottom = yMm / MM_PER_CELL - pixelOffsetTop
    return Offset(cellX, (height - 1) - cellYFromBottom)
}

/** Bitmap cell -> robot mm; exact inverse of [mmToCell] up to rounding. */
internal fun ParsedMap.cellToMm(cell: Offset): Pair<Int, Int> {
    val xMm = ((cell.x + pixelOffsetLeft) * MM_PER_CELL).roundToInt()
    val yMm = (((height - 1) - cell.y + pixelOffsetTop) * MM_PER_CELL).roundToInt()
    return xMm to yMm
}

internal fun ParsedMap.mmToCanvas(xMm: Int, yMm: Int, canvasW: Float, canvasH: Float): Offset {
    val c = mmToCell(xMm, yMm)
    return Offset(c.x * canvasW / width, c.y * canvasH / height)
}

internal fun ParsedMap.canvasToMm(p: Offset, canvasW: Float, canvasH: Float): Pair<Int, Int> =
    cellToMm(Offset(p.x * width / canvasW, p.y * height / canvasH))

/** Even-odd ray cast over the zone's four corners, so rotated zones hit-test correctly. */
internal fun MapZone.contains(xMm: Int, yMm: Int): Boolean {
    val xs = intArrayOf(x0, x1, x2, x3)
    val ys = intArrayOf(y0, y1, y2, y3)
    var inside = false
    var j = 3
    for (i in 0 until 4) {
        if ((ys[i] > yMm) != (ys[j] > yMm)) {
            val crossX = xs[i] + (yMm - ys[i]).toFloat() * (xs[j] - xs[i]) / (ys[j] - ys[i])
            if (xMm < crossX) inside = !inside
        }
        j = i
    }
    return inside
}

/** Shortest distance in mm from a point to the segment A-B. */
internal fun distanceToSegmentMm(px: Int, py: Int, ax: Int, ay: Int, bx: Int, by: Int): Float {
    val dx = (bx - ax).toFloat()
    val dy = (by - ay).toFloat()
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

// ---- Shape manipulation (move / resize / rotate), all in robot millimetres ----

internal fun MapZone.corners(): List<Pair<Int, Int>> = listOf(x0 to y0, x1 to y1, x2 to y2, x3 to y3)

private fun MapZone.withCorners(c: List<Pair<Double, Double>>): MapZone = copy(
    x0 = c[0].first.roundToInt(), y0 = c[0].second.roundToInt(),
    x1 = c[1].first.roundToInt(), y1 = c[1].second.roundToInt(),
    x2 = c[2].first.roundToInt(), y2 = c[2].second.roundToInt(),
    x3 = c[3].first.roundToInt(), y3 = c[3].second.roundToInt(),
)

internal fun MapZone.center(): Pair<Double, Double> =
    (x0 + x1 + x2 + x3) / 4.0 to (y0 + y1 + y2 + y3) / 4.0

internal fun MapZone.translated(dx: Int, dy: Int): MapZone = copy(
    x0 = x0 + dx, y0 = y0 + dy, x1 = x1 + dx, y1 = y1 + dy,
    x2 = x2 + dx, y2 = y2 + dy, x3 = x3 + dx, y3 = y3 + dy,
)

/** Rotate every corner about the zone's centre; corner order is kept. */
internal fun MapZone.rotated(angleRad: Double): MapZone {
    val (cx, cy) = center()
    val cos = kotlin.math.cos(angleRad)
    val sin = kotlin.math.sin(angleRad)
    return withCorners(corners().map { (x, y) ->
        val dx = x - cx
        val dy = y - cy
        (cx + dx * cos - dy * sin) to (cy + dx * sin + dy * cos)
    })
}

/**
 * Move corner [index] of a rectangular zone to (x, y) while the opposite corner stays put and the
 * zone keeps its angle and corner order. Each side is kept at least [minSideMm] and never flips.
 */
internal fun MapZone.resizedFromCorner(index: Int, x: Int, y: Int, minSideMm: Int): MapZone {
    val p = corners().map { it.first.toDouble() to it.second.toDouble() }
    val k = (index + 2) % 4
    val pk = p[k]
    val e1 = p[(k + 1) % 4].let { (it.first - pk.first) to (it.second - pk.second) }
    val e2 = p[(k + 3) % 4].let { (it.first - pk.first) to (it.second - pk.second) }
    val len1 = hypot(e1.first, e1.second)
    val len2 = hypot(e2.first, e2.second)
    if (len1 == 0.0 || len2 == 0.0) return this
    val u = e1.first / len1 to e1.second / len1
    val v = e2.first / len2 to e2.second / len2
    val dx = x - pk.first
    val dy = y - pk.second
    val a = maxOf(dx * u.first + dy * u.second, minSideMm.toDouble())
    val b = maxOf(dx * v.first + dy * v.second, minSideMm.toDouble())
    val out = MutableList(4) { pk }
    out[(k + 1) % 4] = (pk.first + a * u.first) to (pk.second + a * u.second)
    out[(k + 2) % 4] = (pk.first + a * u.first + b * v.first) to (pk.second + a * u.second + b * v.second)
    out[(k + 3) % 4] = (pk.first + b * v.first) to (pk.second + b * v.second)
    return withCorners(out)
}

/** Centre line of a threshold strip: the midpoints of its two short ends (see thresholdStrip). */
internal fun MapZone.thresholdCenterLine(): Pair<Pair<Int, Int>, Pair<Int, Int>> =
    ((x0 + x3) / 2 to (y0 + y3) / 2) to ((x1 + x2) / 2 to (y1 + y2) / 2)

internal fun VirtualWall.translated(dx: Int, dy: Int): VirtualWall =
    VirtualWall(x0 + dx, y0 + dy, x1 + dx, y1 + dy)

internal fun VirtualWall.withEnd(end: Int, x: Int, y: Int): VirtualWall =
    if (end == 0) copy(x0 = x, y0 = y) else copy(x1 = x, y1 = y)

internal fun inMapRange(vararg coords: Int): Boolean = coords.all { it in 0..MAX_MAP_COORD_MM }

internal fun MapZone.fitsMap(): Boolean = inMapRange(x0, y0, x1, y1, x2, y2, x3, y3)
internal fun VirtualWall.fitsMap(): Boolean = inMapRange(x0, y0, x1, y1)
