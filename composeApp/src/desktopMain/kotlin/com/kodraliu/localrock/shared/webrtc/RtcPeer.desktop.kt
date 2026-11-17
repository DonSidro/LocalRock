package com.kodraliu.localrock.shared.webrtc

// Camera live view is hidden on every platform for now (it needs cloud authorisation); the desktop
// build has no WebRTC stack at all.
actual val liveViewSupported: Boolean = false

actual fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer =
    throw UnsupportedOperationException("Camera live view is not available in the desktop app")
