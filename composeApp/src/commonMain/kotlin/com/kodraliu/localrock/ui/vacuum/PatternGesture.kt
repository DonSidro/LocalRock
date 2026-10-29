package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.math.sqrt

/** Include every crossed dot in travel order, even when a device coalesces pointer moves. */
internal fun patternSegmentHits(centers: List<Offset>, from: Offset, to: Offset, radius: Float): List<Int> {
    val delta = to - from
    val lengthSquared = delta.getDistanceSquared()
    return centers.mapIndexedNotNull { index, center ->
        val relative = center - from
        val t = if (lengthSquared == 0f) 0f else
            ((relative.x * delta.x + relative.y * delta.y) / lengthSquared).coerceIn(0f, 1f)
        val distanceSquared = (center - (from + delta * t)).getDistanceSquared()
        if (distanceSquared > radius * radius) null else {
            val entry = if (lengthSquared == 0f) 0f else
                (t - sqrt((radius * radius - distanceSquared) / lengthSquared)).coerceAtLeast(0f)
            index to entry
        }
    }.sortedBy { it.second }.map { it.first }
}

internal fun appendPatternDot(selected: MutableList<Int>, hit: Int) {
    if (hit !in 0..8 || hit in selected) return
    selected.lastOrNull()?.let { previous ->
        val rowSum = previous / 3 + hit / 3
        val colSum = previous % 3 + hit % 3
        if (rowSum % 2 == 0 && colSum % 2 == 0) {
            val between = (rowSum / 2) * 3 + colSum / 2
            if (between != previous && between !in selected) selected += between
        }
    }
    selected += hit
}

/** The existing protocol remains row-major 1..9; incomplete gestures cannot reuse an old PIN. */
internal fun patternDigits(selected: List<Int>): String =
    if (selected.size >= 4) selected.joinToString("") { (it + 1).toString() } else ""
