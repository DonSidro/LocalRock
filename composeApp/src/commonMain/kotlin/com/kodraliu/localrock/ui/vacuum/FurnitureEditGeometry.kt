package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.FurniturePlacement
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.math.atan2
import kotlin.math.roundToInt

internal enum class FurnitureHandle(val corner: Int) {
    DELETE(0), ROTATE(1), MOVE(3), RESIZE(2),
}

/** Screen-space decoration only: padding must never become part of the saved footprint. */
internal fun furnitureSelectionCorners(points: List<Offset>, gap: Float, minSide: Float): List<Offset> {
    val width = (points[1] - points[0]).getDistance()
    val height = (points[3] - points[0]).getDistance()
    if (width <= 0f || height <= 0f) return points
    val u = (points[1] - points[0]) / width
    val v = (points[3] - points[0]) / height
    val x = maxOf(gap, (minSide - width) / 2)
    val y = maxOf(gap, (minSide - height) / 2)
    return listOf(points[0] - u * x - v * y, points[1] + u * x - v * y,
        points[2] + u * x + v * y, points[3] - u * x + v * y)
}

internal fun furnitureHandleAt(point: Offset, corners: List<Offset>, radius: Float): FurnitureHandle? =
    FurnitureHandle.entries.minByOrNull { (corners[it.corner] - point).getDistance() }
        ?.takeIf { (corners[it.corner] - point).getDistance() <= radius }

/** Total pointer delta from the initial grab, so grabbing a padded handle never jumps the item. */
internal fun FurniturePlacement.resizedFromHandle(dx: Int, dy: Int): FurniturePlacement {
    if (dx == 0 && dy == 0) return this
    val a = corners[0]
    val ux = (corners[1].x - a.x) / widthMm
    val uy = (corners[1].y - a.y) / widthMm
    val vx = (corners[3].x - a.x) / depthMm
    val vy = (corners[3].y - a.y) / depthMm
    // One mm of rounding allowance keeps rotated edges above the 100 mm validation minimum.
    val width = maxOf(101.0, widthMm + dx * ux + dy * uy)
    val height = maxOf(101.0, depthMm + dx * vx + dy * vy)
    fun point(x: Int, y: Int) = ParsedMapPoint(
        (a.x + x * ux * width + y * vx * height).roundToInt(),
        (a.y + x * uy * width + y * vy * height).roundToInt(),
    )
    return copy(corners = listOf(a, point(1, 0), point(1, 1), point(0, 1)))
}

/** Robot-space vectors already account for the map's Y flip. */
internal fun FurniturePlacement.rotatedFromHandle(start: Pair<Int, Int>, end: Pair<Int, Int>): FurniturePlacement {
    if (start == end) return this
    val cx = corners.sumOf { it.x }.toDouble() / 4
    val cy = corners.sumOf { it.y }.toDouble() / 4
    val ax = start.first - cx; val ay = start.second - cy
    val bx = end.first - cx; val by = end.second - cy
    if (ax * ax + ay * ay < 1 || bx * bx + by * by < 1) return this
    return rotated(atan2(ax * by - ay * bx, ax * bx + ay * by) * 180 / kotlin.math.PI)
}
