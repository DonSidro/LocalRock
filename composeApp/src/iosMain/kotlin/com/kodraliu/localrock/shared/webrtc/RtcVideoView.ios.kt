package com.kodraliu.localrock.shared.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * Hosts the Swift peer's `RTCMTLVideoView` inside Compose. The view is created and owned by the
 * Swift [RtcNativePeer]; here we just embed it. Reading [RtcPeer.connectionState] keeps this
 * recomposing as the stream comes up.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)
@Composable
actual fun RtcVideoView(peer: RtcPeer, modifier: Modifier) {
    val iosPeer = peer as? IosRtcPeer ?: return
    @Suppress("UNUSED_VARIABLE")
    val state by iosPeer.connectionState.collectAsState()
    val view: UIView = iosPeer.renderView() ?: return
    UIKitView(
        factory = { view },
        modifier = modifier,
    )
}
