package com.kodraliu.localrock.shared.webrtc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
actual fun RtcVideoView(peer: RtcPeer, modifier: Modifier) {
    val androidPeer = peer as? AndroidRtcPeer ?: return
    val track by androidPeer.videoTrack.collectAsState()
    val rendererState = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(rtcEglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                setEnableHardwareScaler(true)
                rendererState.value = this
            }
        },
    )

    DisposableEffect(track, rendererState.value) {
        val renderer = rendererState.value
        val t = track
        if (renderer != null && t != null) t.addSink(renderer)
        onDispose {
            if (renderer != null && t != null) runCatching { t.removeSink(renderer) }
        }
    }

    DisposableEffect(Unit) {
        onDispose { rendererState.value?.release() }
    }
}
