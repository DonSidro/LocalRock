package com.kodraliu.localrock.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * A Scaffold's content padding without the bottom inset. The app draws edge to edge: scrolling
 * content and bottom panels extend behind the transparent navigation bar and apply the bottom
 * inset themselves (as scroll padding, or inside the panel), instead of stopping above the bar.
 */
@Composable
fun PaddingValues.exceptBottom(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
    )
}
