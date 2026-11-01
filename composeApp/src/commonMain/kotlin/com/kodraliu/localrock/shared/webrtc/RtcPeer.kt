package com.kodraliu.localrock.shared.webrtc

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class RtcIceServer(
    val url: String,
    val username: String = "",
    val password: String = "",
)

data class RtcIceCandidate(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
)

enum class RtcConnectionState { NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED }

/** Audio first, send/receive capabilities and a named audio stream, without local capture tracks. */
data class RtcOfferOptions(val compactCodecs: Boolean = false)

interface RtcPeer {
    val localCandidates: SharedFlow<RtcIceCandidate>
    val connectionState: StateFlow<RtcConnectionState>

    suspend fun createOffer(options: RtcOfferOptions = RtcOfferOptions()): String
    suspend fun setRemoteAnswer(sdp: String)
    suspend fun addRemoteCandidate(candidate: RtcIceCandidate)
    suspend fun diagnostics(): RtcDiagnostics
    val supportsVoiceCall: Boolean get() = false
    suspend fun requestMicrophonePermission(): Boolean = false
    suspend fun startVoiceCall() { error("Voice calls are unavailable on this platform") }
    fun setMicrophoneMuted(muted: Boolean) {}
    fun setRemoteAudioMuted(muted: Boolean) {}
    fun stopVoiceCall() {}
    fun close()
}

expect val liveViewSupported: Boolean

expect fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer


