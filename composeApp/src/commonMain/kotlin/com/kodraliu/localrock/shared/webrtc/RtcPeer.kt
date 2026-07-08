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


interface RtcPeer {
    val localCandidates: SharedFlow<RtcIceCandidate>
    val connectionState: StateFlow<RtcConnectionState>

    suspend fun createOffer(): String
    suspend fun setRemoteAnswer(sdp: String)
    fun addRemoteCandidate(candidate: RtcIceCandidate)
    fun close()
}

expect val liveViewSupported: Boolean

expect fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer
