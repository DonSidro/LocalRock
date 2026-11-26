package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import kotlin.math.pow

/** Zoom factor for one wheel notch; touchpads send fractions of a notch and zoom smoothly. */
private const val WHEEL_ZOOM_PER_NOTCH = 1.2f

/**
 * Calls [onZoom] for each mouse wheel (or touchpad scroll) step over this element, with the cursor
 * position, the zoom factor the step asks for (above 1 zooms in) and the element's size.
 * Touch screens never send scroll events, so this changes nothing on phones.
 */
internal fun Modifier.onMouseWheelZoom(
    vararg keys: Any?,
    onZoom: (cursor: Offset, factor: Float, view: Size) -> Unit,
): Modifier = pointerInput(*keys) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            val notches = change.scrollDelta.y
            if (notches == 0f) continue
            // Wheel forward (negative delta) zooms in, as in map and image apps.
            onZoom(change.position, WHEEL_ZOOM_PER_NOTCH.pow(-notches), size.toSize())
            change.consume()
        }
    }
}

/**
 * Zoom a scale-and-offset map view with the mouse wheel, about the point under the cursor.
 *
 * [content] gives the map's fitted size for a view size; [read] and [write] reach the view's
 * current scale and offset. They are read on every event, so they may close over changing state.
 */
internal fun Modifier.mouseWheelZoom(
    key: Any?,
    content: (view: Size) -> Size,
    read: () -> Pair<Float, Offset>,
    write: (scale: Float, offset: Offset) -> Unit,
): Modifier = onMouseWheelZoom(key) { cursor, factor, view ->
    val (scale, offset) = read()
    val (newScale, newOffset) = zoomMap(scale, offset, cursor, Offset.Zero, factor, content(view), view)
    write(newScale, newOffset)
}
