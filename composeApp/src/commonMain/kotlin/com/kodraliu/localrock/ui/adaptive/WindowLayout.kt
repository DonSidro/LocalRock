package com.kodraliu.localrock.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

data class WindowLayout(val twoPane: Boolean)

private const val TWO_PANE_MIN_WIDTH_DP = 600f
private const val TWO_PANE_MIN_HEIGHT_DP = 500f

fun windowLayoutFor(widthDp: Float, heightDp: Float): WindowLayout =
    WindowLayout(twoPane = widthDp >= TWO_PANE_MIN_WIDTH_DP && heightDp >= TWO_PANE_MIN_HEIGHT_DP)

val LocalWindowLayout: ProvidableCompositionLocal<WindowLayout> =
    compositionLocalOf { WindowLayout(twoPane = false) }

@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current.density
    val widthDp = windowInfo.containerSize.width / density
    val heightDp = windowInfo.containerSize.height / density
    val layout = remember(widthDp, heightDp) {
        windowLayoutFor(widthDp, heightDp)
    }
    CompositionLocalProvider(LocalWindowLayout provides layout, content = content)
}
