package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Only changes of coordinate space or selected map invalidate a user's viewport. */
internal fun ParsedMap.viewportIdentity(scope: Any?): List<Any?> = listOf(
    scope, format, width, height, resolution, pixelOffsetLeft, pixelOffsetTop,
)

/**
 * The user's zoom (a multiple of the framed view) and pan (from the view's centre). It is held above the
 * compact and two-pane layouts so folding or unfolding keeps it. Because both are relative to the framed
 * view and its centre, a window that changes size keeps the zoom and the map point under the centre, and a
 * view the user has not moved (zoom 1, no pan) is framed afresh for the new size.
 */
internal class LiveMapViewport {
    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)
    private var key: Any? = null

    /** Frames the view afresh when [newKey] (the map's identity and the view mode) is not the one it was framed for. */
    fun frameIfNeeded(newKey: Any?) {
        if (key == newKey) return
        key = newKey
        reset()
    }

    fun reset() {
        zoom = 1f
        pan = Offset.Zero
    }
}
