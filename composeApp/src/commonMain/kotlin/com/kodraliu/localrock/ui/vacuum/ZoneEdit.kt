package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.ZoneRect

/** The smallest side a zone may have, in robot millimetres; the same limit a newly drawn zone has. */
internal const val MIN_ZONE_SIDE_MM = 500

/** A zone's corners in robot millimetres, in the order [ZoneRect.corner] numbers them. */
internal fun ZoneRect.corners(): List<Pair<Int, Int>> = List(4) { corner(it) }

/** Corner [index]: 0 is (x1, y1), 1 is (x2, y1), 2 is (x2, y2), 3 is (x1, y2). */
internal fun ZoneRect.corner(index: Int): Pair<Int, Int> = when (index) {
    0 -> x1 to y1
    1 -> x2 to y1
    2 -> x2 to y2
    else -> x1 to y2
}

/** The zone moved by [dx], [dy] millimetres, keeping its size. */
internal fun ZoneRect.movedBy(dx: Int, dy: Int): ZoneRect = copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)

/**
 * The zone with corner [index] dragged to ([xMm], [yMm]) and the opposite corner left where it is. The
 * corners are put back in order, so a corner dragged past its neighbours flips the zone instead of
 * turning it inside out, and each side is kept at least [minSide] long by pushing the dragged corner
 * away from the fixed one.
 */
internal fun ZoneRect.withCorner(index: Int, xMm: Int, yMm: Int, minSide: Int = MIN_ZONE_SIDE_MM): ZoneRect {
    val (fx, fy) = corner((index + 2) % 4)
    val x = if (xMm >= fx) maxOf(xMm, fx + minSide) else minOf(xMm, fx - minSide)
    val y = if (yMm >= fy) maxOf(yMm, fy + minSide) else minOf(yMm, fy - minSide)
    return copy(x1 = minOf(x, fx), y1 = minOf(y, fy), x2 = maxOf(x, fx), y2 = maxOf(y, fy))
}
