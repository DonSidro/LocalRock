package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Only changes of coordinate space or selected map invalidate a user's viewport. */
internal fun ParsedMap.viewportIdentity(scope: Any?): List<Any?> = listOf(
    scope, format, width, height, resolution, pixelOffsetLeft, pixelOffsetTop,
)

internal class LiveMapViewport {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    private var identity: Any? = null
    private var framedSize: Size? = null

    fun frameIfNeeded(key: Any?, size: Size, fit: Pair<Float, Offset>?) {
        if (fit == null || size.width <= 0f || size.height <= 0f) return
        if (identity != key || framedSize != size) {
            reset(fit)
            identity = key
            framedSize = size
        }
    }

    fun reset(fit: Pair<Float, Offset>) {
        scale = fit.first
        offset = fit.second
    }
}
