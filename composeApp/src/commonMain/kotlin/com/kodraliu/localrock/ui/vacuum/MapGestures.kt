package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.PI
import kotlin.math.abs

/**
 * Pan, pinch-zoom and twist, like `detectTransformGestures`, but also telling the caller how many
 * fingers are down. That is the one thing it hides, and the 3D map needs it: one finger moves the
 * map, while two fingers dragged together tilt the camera, as in a maps app's 3D view.
 *
 * Nothing is reported, and nothing consumed, until the touches have moved past the touch slop, so a
 * plain tap still reaches the tap detector.
 */
internal suspend fun PointerInputScope.detectMapGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, fingers: Int) -> Unit,
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val cancelled = event.changes.any { it.isConsumed }
            if (!cancelled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()
                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    if (rotationChange != 0f || zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(
                            event.calculateCentroid(useCurrent = false),
                            panChange,
                            zoomChange,
                            rotationChange,
                            event.changes.count { it.pressed },
                        )
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!cancelled && event.changes.any { it.pressed })
    }
}

/**
 * Whether a two-finger movement is a camera tilt: the fingers dragged together, mostly up or down, without
 * pinching or twisting. Anything else is a pinch, a twist or a pan, so a pinch whose midpoint drifts does
 * not also tip the camera, and two fingers can still pan the 3D model.
 */
internal fun isTiltDrag(fingers: Int, pan: Offset, zoom: Float, rotation: Float): Boolean =
    fingers >= 2 &&
        abs(zoom - 1f) < TILT_MAX_ZOOM_CHANGE &&
        abs(rotation) < TILT_MAX_ROTATION_DEGREES &&
        abs(pan.y) > 2f * abs(pan.x)

private const val TILT_MAX_ZOOM_CHANGE = 0.015f
private const val TILT_MAX_ROTATION_DEGREES = 1.5f
