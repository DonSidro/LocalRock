package com.kodraliu.localrock.shared.webrtc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS WebRTC lives on the Swift side (Apple's `WebRTC.framework`), because there is no in-Kotlin
 * WebRTC engine for Kotlin/Native. This mirrors the MQTT approach: the Swift app implements the
 * callback-based, non-suspend [RtcNativePeerFactory] and injects it via `MainViewController`.
 *
 * See `iosApp/iosApp/WebRtcPeer.swift`.
 */

data class RtcNativeIceServer(val url: String, val username: String, val password: String)

/** Callbacks the Swift peer fires back into Kotlin. */
interface RtcNativePeerListener {
    fun onLocalCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)

    /** One of: "new", "connecting", "connected", "disconnected", "failed", "closed". */
    fun onConnectionState(state: String)
}

/** A single peer connection, implemented in Swift. Non-suspend so Swift can implement it. */
interface RtcNativePeer {
    /** Adds recv-only transceivers, creates the offer, and sets it as the local description. */
    fun createOffer(onSuccess: (String) -> Unit, onError: (String) -> Unit)
    fun setRemoteAnswer(sdp: String)
    fun addRemoteCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String)
    fun close()

    /** The `RTCMTLVideoView` (a `UIView`) that renders the remote track, or null before it arrives. */
    fun renderView(): UIView?
}

interface RtcNativePeerFactory {
    fun create(iceServers: List<RtcNativeIceServer>, listener: RtcNativePeerListener): RtcNativePeer
}

/** Holds the Swift-injected factory; set once from `MainViewController(rtcPeerFactory:)`. */
object IosRtc {
    var factory: RtcNativePeerFactory? = null
}

actual val liveViewSupported: Boolean
    get() = IosRtc.factory != null

actual fun createRtcPeer(iceServers: List<RtcIceServer>): RtcPeer {
    val factory = IosRtc.factory
        ?: throw UnsupportedOperationException(
            "iOS WebRTC factory not injected — pass one to MainViewController(rtcPeerFactory:)"
        )
    return IosRtcPeer(iceServers, factory)
}

internal class IosRtcPeer(
    iceServers: List<RtcIceServer>,
    factory: RtcNativePeerFactory,
) : RtcPeer, RtcNativePeerListener {

    private val _localCandidates =
        MutableSharedFlow<RtcIceCandidate>(replay = 32, extraBufferCapacity = 32)
    override val localCandidates: SharedFlow<RtcIceCandidate> = _localCandidates

    private val _connectionState = MutableStateFlow(RtcConnectionState.NEW)
    override val connectionState: StateFlow<RtcConnectionState> = _connectionState

    private val native: RtcNativePeer = factory.create(
        iceServers.map { RtcNativeIceServer(it.url, it.username, it.password) },
        this,
    )

    override suspend fun createOffer(): String = suspendCancellableCoroutine { cont ->
        native.createOffer(
            onSuccess = { sdp -> cont.resume(sdp) },
            onError = { msg -> cont.resumeWithException(RuntimeException("createOffer failed: $msg")) },
        )
    }

    override suspend fun setRemoteAnswer(sdp: String) {
        native.setRemoteAnswer(sdp)
    }

    override fun addRemoteCandidate(candidate: RtcIceCandidate) {
        native.addRemoteCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
    }

    override fun close() {
        if (_connectionState.value != RtcConnectionState.CLOSED) {
            _connectionState.value = RtcConnectionState.CLOSED
        }
        native.close()
    }

    internal fun renderView(): UIView? = native.renderView()

    // ---- RtcNativePeerListener (invoked from Swift) ----

    override fun onLocalCandidate(sdpMid: String?, sdpMLineIndex: Int, candidate: String) {
        _localCandidates.tryEmit(RtcIceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    override fun onConnectionState(state: String) {
        if (_connectionState.value == RtcConnectionState.CLOSED) return
        _connectionState.value = when (state.lowercase()) {
            "new" -> RtcConnectionState.NEW
            "connecting" -> RtcConnectionState.CONNECTING
            "connected" -> RtcConnectionState.CONNECTED
            "disconnected" -> RtcConnectionState.DISCONNECTED
            "failed" -> RtcConnectionState.FAILED
            "closed" -> RtcConnectionState.CLOSED
            else -> _connectionState.value
        }
    }
}
