package com.kodraliu.localrock.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centres a screen's body at a readable maximum width while the surrounding background still fills
 * the window. The outer [Box] fills its parent, and the content is drawn in a horizontally centred
 * column capped at [maxWidth]. Below [maxWidth] the column fills the window, so phones are unchanged.
 */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 640.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxHeight()) {
            content()
        }
    }
}

/** The width a centred body gets for a given available width: the smaller of the two. */
internal fun contentWidthDp(availableDp: Float, maxDp: Float): Float = minOf(availableDp, maxDp)
