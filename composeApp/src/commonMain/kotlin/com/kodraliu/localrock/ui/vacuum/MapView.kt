package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The single transform every part of the map goes through, so nothing can drift out of step with
 * anything else: the floor, the wall layers, the room labels and a tap's hit-test all use [toScreen]
 * or [toBitmap]. A point in the flat bitmap is turned by [yawDegrees] about the floor plan's centre,
 * squashed vertically by [squash] for a tilted camera (1 is the flat top-down view), then scaled by
 * [k] and moved by [pan]. [lift] is how far one unit of height rises on screen, which is what makes
 * a wall of any height line up under the same rotation.
 */
internal class MapView(
    val canvasW: Float,
    val canvasH: Float,
    val centerX: Float,
    val centerY: Float,
    val k: Float,
    val pan: Offset,
    val yawDegrees: Float,
    val squash: Float,
) {
    private val radians = yawDegrees * (PI / 180.0)
    private val cosT = cos(radians).toFloat()
    private val sinT = sin(radians).toFloat()
    val lift: Float = sqrt((1f - squash * squash).coerceAtLeast(0f))

    fun toScreen(px: Float, py: Float, height: Float = 0f): Offset {
        val dx = px - centerX
        val dy = py - centerY
        val x = dx * cosT - dy * sinT
        val y = (dx * sinT + dy * cosT) * squash
        return Offset(canvasW / 2f + pan.x + x * k, canvasH / 2f + pan.y + y * k - height * k * lift)
    }

    /** The point on the floor under a screen position; the inverse of [toScreen] at height 0. */
    fun toBitmap(sx: Float, sy: Float): Offset {
        val x = (sx - canvasW / 2f - pan.x) / k
        val y = (sy - canvasH / 2f - pan.y) / k / squash
        return Offset(centerX + x * cosT + y * sinT, centerY - x * sinT + y * cosT)
    }
}

/**
 * The scale at which the floor plan fits the canvas. The 3D view turns freely, so it fits the plan's
 * diagonal and stays the same size at every heading; the flat view fits the plan as it is turned.
 */
internal fun fitScale(
    canvasW: Float, canvasH: Float,
    contentW: Float, contentH: Float,
    yawDegrees: Float, squash: Float, freeTurning: Boolean,
): Float {
    if (canvasW <= 0f || canvasH <= 0f || contentW <= 0f || contentH <= 0f) return 1f
    val extentW: Float
    val extentH: Float
    if (freeTurning) {
        val diagonal = hypot(contentW, contentH)
        extentW = diagonal
        extentH = diagonal * squash
    } else {
        val r = yawDegrees * (PI / 180.0)
        val c = abs(cos(r)).toFloat()
        val s = abs(sin(r)).toFloat()
        extentW = contentW * c + contentH * s
        extentH = (contentW * s + contentH * c) * squash
    }
    return minOf(canvasW / extentW, canvasH / extentH)
}
