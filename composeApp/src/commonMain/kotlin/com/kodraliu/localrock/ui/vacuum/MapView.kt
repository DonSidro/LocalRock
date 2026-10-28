package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
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

// The 3D camera's tip from looking straight down, in degrees. Past about 75 the floor is a sliver.
internal const val MAP_3D_MIN_TILT = 0f
internal const val MAP_3D_MAX_TILT = 75f

/** How much a floor squashes vertically when the camera is tipped [tiltDegrees] off straight down. */
internal fun squashForTilt(tiltDegrees: Float): Float = cos(tiltDegrees * (PI / 180.0)).toFloat()

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

/**
 * The view for a canvas whose floor plan is framed with scale [fit]. The user's [zoom] is a multiple of that
 * frame and their [pan] is in units of it, so a canvas that changes size (a fold, a rotation of the phone)
 * keeps the zoom and the point of the plan under its centre.
 */
internal fun framedView(
    canvasW: Float, canvasH: Float, centerX: Float, centerY: Float,
    fit: Float, zoom: Float, pan: Offset, yawDegrees: Float, squash: Float,
): MapView = MapView(
    canvasW = canvasW, canvasH = canvasH, centerX = centerX, centerY = centerY,
    k = fit * zoom, pan = pan * fit, yawDegrees = yawDegrees, squash = squash,
)

/**
 * The matrix that carries a layer pixel [height] up to the screen, built in the order [MapView.toScreen] uses:
 * turn about the plan's centre first, then squash in screen space as a tilted camera does. Floor, walls and
 * route all draw through it, so they cannot drift from the labels and taps at any heading or tilt.
 */
internal fun MapView.layerMatrix(height: Float): Matrix = Matrix().apply {
    translate(0f, -height * k * lift)
    translate(canvasW / 2f + pan.x, canvasH / 2f + pan.y)
    scale(k, k)
    scale(1f, squash)
    rotateZ(yawDegrees)
    translate(-centerX, -centerY)
}
