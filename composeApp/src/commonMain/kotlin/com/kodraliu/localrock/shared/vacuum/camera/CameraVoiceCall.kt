package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.webrtc.RtcPeer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.*

/** A call belongs to one live preview. Local audio stops immediately; remote cleanup is bounded. */
class CameraVoiceCall internal constructor(
    private val rpc: CameraRpc,
    private val scope: CoroutineScope,
    private val currentPeer: () -> RtcPeer?,
    private val currentClientId: () -> String?,
    private val streaming: () -> Boolean,
    private val commandTimeoutMs: Long = 5_000,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _microphoneMuted = MutableStateFlow(true)
    val microphoneMuted: StateFlow<Boolean> = _microphoneMuted
    private val _remoteAudioMuted = MutableStateFlow(true)
    val remoteAudioMuted: StateFlow<Boolean> = _remoteAudioMuted
    private val _speakerVolume = MutableStateFlow<Int?>(null)
    val speakerVolume: StateFlow<Int?> = _speakerVolume
    private val _volumeBusy = MutableStateFlow(false)
    val volumeBusy: StateFlow<Boolean> = _volumeBusy
    private var job: Job? = null
    private var volumeJob: Job? = null
    private var callPeer: RtcPeer? = null
    private var callClientId: String? = null
    private var blockedPeer: RtcPeer? = null

    fun start() {
        if (job != null || !streaming()) return
        val peer = currentPeer() ?: return
        val id = currentClientId() ?: return
        if (!peer.supportsVoiceCall) {
            _error.value = "Voice calls are available in the iPhone test build only."
            return
        }
        if (peer === blockedPeer) {
            _error.value = "The previous call could not be confirmed. End video and reconnect before trying again."
            return
        }
        _error.value = null
        _busy.value = true
        _speakerVolume.value = null
        callPeer = peer
        callClientId = id
        val next = scope.launch(start = CoroutineStart.LAZY) {
            var requested = false
            try {
                if (!peer.requestMicrophonePermission()) {
                    _error.value = "Microphone permission was not granted. Enable it for LocalRock in iPhone Settings to make a call."
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                checkCurrent(peer, id)
                withTimeout(commandTimeoutMs) { requireOwner(id) }
                // Finish an issued start before cleanup, including when End call is tapped meanwhile.
                withContext(NonCancellable) {
                    withTimeout(commandTimeoutMs) {
                        requested = true
                        acknowledge("start_voice_chat", buildJsonObject {
                            put("record", true); put("play", true)
                        })
                    }
                }
                currentCoroutineContext().ensureActive()
                checkCurrent(peer, id)
                withTimeout(commandTimeoutMs) { peer.startVoiceCall() }
                currentCoroutineContext().ensureActive()
                checkCurrent(peer, id)
                _microphoneMuted.value = false
                _remoteAudioMuted.value = false
                _active.value = true
                _busy.value = false
                awaitCancellation()
            } catch (_: TimeoutCancellationException) {
                if (requested) blockedPeer = peer
                _error.value = "Call setup timed out. End video and reconnect before retrying."
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _error.value = "The voice call could not start. Video remains available."
            } finally {
                peer.stopVoiceCall()
                _active.value = false
                _microphoneMuted.value = true
                _remoteAudioMuted.value = true
                _busy.value = true
                volumeJob?.cancel()
                withContext(NonCancellable) {
                    volumeJob?.join()
                    if (requested) try {
                        withTimeout(commandTimeoutMs) {
                            if (owner() == id) acknowledge("stop_voice_chat")
                        }
                    } catch (_: Exception) {
                        blockedPeer = peer
                        _error.value = "Phone audio is off, but the robot did not confirm call cleanup. End video before retrying."
                    }
                }
                callPeer = null
                callClientId = null
                job = null
                _busy.value = false
            }
        }
        job = next
        next.invokeOnCompletion {
            if (job === next) { job = null; callPeer = null; callClientId = null; _busy.value = false }
        }
        next.start()
    }

    fun stop() {
        callPeer?.stopVoiceCall()
        _active.value = false
        _microphoneMuted.value = true
        _remoteAudioMuted.value = true
        volumeJob?.cancel()
        if (job != null) { _busy.value = true; job?.cancel() }
    }

    internal suspend fun release() {
        val pending = job
        stop()
        pending?.join()
    }

    fun muteMicrophone(muted: Boolean) {
        if (!_active.value) return
        callPeer?.setMicrophoneMuted(muted)
        _microphoneMuted.value = muted
    }

    fun muteRemoteAudio(muted: Boolean) {
        if (!_active.value) return
        callPeer?.setRemoteAudioMuted(muted)
        _remoteAudioMuted.value = muted
    }

    fun setSpeakerVolume(volume: Int) {
        if (!_active.value || volumeJob != null || volume !in 0..100) return
        val peer = callPeer ?: return
        val id = callClientId ?: return
        _volumeBusy.value = true
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withTimeout(commandTimeoutMs) {
                    checkCurrent(peer, id)
                    requireOwner(id)
                    acknowledge("set_voice_chat_volume", buildJsonObject { put("volume", volume) })
                    if (_active.value && callPeer === peer) _speakerVolume.value = volume
                }
            } catch (_: TimeoutCancellationException) {
                _error.value = "The robot speaker volume change was not confirmed."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { _error.value = "The robot did not confirm the speaker volume change." }
            finally { volumeJob = null; _volumeBusy.value = false }
        }
        volumeJob = next
        next.invokeOnCompletion { if (volumeJob === next) { volumeJob = null; _volumeBusy.value = false } }
        next.start()
    }

    private fun checkCurrent(peer: RtcPeer, id: String) {
        check(streaming() && currentPeer() === peer && currentClientId() == id)
    }

    private suspend fun requireOwner(id: String) { check(owner() == id) }

    private suspend fun owner(): String {
        val response = rpc.call("get_homesec_connect_status", JsonArray(emptyList()))
        check(response.error == null)
        val result = if (response.result is JsonArray) response.result.singleOrNull() else response.result
        return (result as? JsonObject)?.get("client_id")?.jsonPrimitive?.contentOrNull
            ?: error("Camera ownership unavailable")
    }

    private suspend fun acknowledge(method: String, params: JsonElement = JsonArray(emptyList())) {
        val response = rpc.call(method, params)
        check(response.error == null)
        val result = if (response.result is JsonArray) response.result.singleOrNull() else response.result
        check(result == JsonPrimitive("ok"))
    }
}
