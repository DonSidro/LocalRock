package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.webrtc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CameraVoiceCallTest {
    private class Peer : RtcPeer {
        override val localCandidates = MutableSharedFlow<RtcIceCandidate>()
        override val connectionState = MutableStateFlow(RtcConnectionState.CONNECTED)
        override val supportsVoiceCall = true
        var permission = true
        var permissionGate: CompletableDeferred<Boolean>? = null
        var nativeFailure = false
        var audio = false
        var starts = 0
        var microphoneIsMuted = true
        var remoteIsMuted = true
        override suspend fun createOffer(options: RtcOfferOptions): String = error("Call must reuse the offer")
        override suspend fun setRemoteAnswer(sdp: String) = error("Call must reuse the answer")
        override suspend fun addRemoteCandidate(candidate: RtcIceCandidate) = error("Call must reuse ICE")
        override suspend fun diagnostics() = RtcDiagnostics()
        override suspend fun requestMicrophonePermission() = permissionGate?.await() ?: permission
        override suspend fun startVoiceCall() {
            if (nativeFailure) error("private-native-message")
            starts++; audio = true; microphoneIsMuted = false; remoteIsMuted = false
        }
        override fun setMicrophoneMuted(muted: Boolean) { microphoneIsMuted = muted }
        override fun setRemoteAudioMuted(muted: Boolean) { remoteIsMuted = muted }
        override fun stopVoiceCall() { audio = false; microphoneIsMuted = true; remoteIsMuted = true }
        override fun close() = stopVoiceCall()
    }

    private class Rpc : CameraRpc {
        val calls = mutableListOf<Pair<String, JsonElement>>()
        var owner = "our-preview"
        var startDelay = 0L
        var stopDelay = 0L
        var failMethod: String? = null
        override suspend fun call(method: String, params: JsonElement): V1Response {
            calls += method to params
            if (method == failMethod) return V1Response(null, null, buildJsonObject { put("message", "private-robot-message") })
            if (method == "start_voice_chat") delay(startDelay)
            if (method == "stop_voice_chat") delay(stopDelay)
            val result = if (method == "get_homesec_connect_status") buildJsonObject { put("client_id", owner) }
                else JsonArray(listOf(JsonPrimitive("ok")))
            return V1Response(null, result, null)
        }
        fun count(method: String) = calls.count { it.first == method }
    }

    private class Rig(scope: CoroutineScope) {
        val rpc = Rpc()
        val peer = Peer()
        var current: RtcPeer? = peer
        var streaming = true
        val voice = CameraVoiceCall(rpc, scope, { current }, { "our-preview" }, { streaming }, 500)
    }

    @Test fun denied_permission_never_starts_robot_or_phone_audio() = runTest {
        val r = Rig(backgroundScope); r.peer.permission = false
        r.voice.start(); runCurrent()
        assertEquals(0, r.rpc.count("start_voice_chat"))
        assertEquals(0, r.peer.starts)
        assertFalse(r.voice.active.value)
        assertFalse(r.voice.busy.value)
        assertContains(r.voice.error.value.orEmpty(), "permission")
    }

    @Test fun call_requires_streaming_video_and_current_ownership() = runTest {
        val r = Rig(backgroundScope); r.streaming = false
        r.voice.start(); runCurrent(); assertTrue(r.rpc.calls.isEmpty())
        r.streaming = true; r.rpc.owner = "oem-app"
        r.voice.start(); runCurrent()
        assertEquals(0, r.rpc.count("start_voice_chat"))
        assertEquals(0, r.rpc.count("stop_voice_chat"))
        assertEquals(0, r.peer.starts)
    }

    @Test fun confirmed_call_uses_both_audio_directions_without_replacing_video() = runTest {
        val r = Rig(backgroundScope)
        r.voice.start(); runCurrent()
        assertTrue(r.voice.active.value); assertTrue(r.peer.audio)
        assertFalse(r.voice.microphoneMuted.value); assertFalse(r.voice.remoteAudioMuted.value)
        val params = r.rpc.calls.single { it.first == "start_voice_chat" }.second
        assertEquals(Json.parseToJsonElement("{\"record\":true,\"play\":true}"), params)
        r.voice.start(); runCurrent()
        assertEquals(1, r.peer.starts); assertEquals(1, r.rpc.count("start_voice_chat"))
        r.voice.stop(); runCurrent()
    }

    @Test fun microphone_and_playback_mute_are_independent_and_immediate() = runTest {
        val r = Rig(backgroundScope); r.voice.start(); runCurrent()
        val requests = r.rpc.calls.size
        r.voice.muteMicrophone(true)
        assertTrue(r.peer.microphoneIsMuted); assertFalse(r.peer.remoteIsMuted)
        r.voice.muteRemoteAudio(true)
        assertTrue(r.peer.remoteIsMuted)
        r.voice.muteMicrophone(false)
        assertFalse(r.peer.microphoneIsMuted); assertTrue(r.peer.remoteIsMuted)
        assertEquals(requests, r.rpc.calls.size)
        r.voice.stop(); runCurrent()
    }

    @Test fun stopping_mutes_locally_before_remote_cleanup_finishes() = runTest {
        val r = Rig(backgroundScope); r.rpc.stopDelay = 300
        r.voice.start(); runCurrent(); r.voice.stop()
        assertFalse(r.peer.audio); assertFalse(r.voice.active.value)
        runCurrent(); assertTrue(r.voice.busy.value)
        advanceTimeBy(301); runCurrent()
        assertEquals(1, r.rpc.count("stop_voice_chat")); assertFalse(r.voice.busy.value)
    }

    @Test fun cancelling_an_issued_start_waits_then_releases_without_opening_microphone() = runTest {
        val r = Rig(backgroundScope); r.rpc.startDelay = 300
        r.voice.start(); runCurrent(); r.voice.stop(); runCurrent()
        assertEquals(0, r.peer.starts); assertEquals(0, r.rpc.count("stop_voice_chat"))
        r.voice.start()
        advanceTimeBy(301); runCurrent()
        assertEquals(1, r.rpc.count("start_voice_chat"))
        assertEquals(1, r.rpc.count("stop_voice_chat"))
        assertEquals(0, r.peer.starts); assertFalse(r.voice.active.value)
    }

    @Test fun late_permission_result_after_stop_does_not_start_a_call() = runTest {
        val r = Rig(backgroundScope); val gate = CompletableDeferred<Boolean>()
        r.peer.permissionGate = gate
        r.voice.start(); runCurrent(); r.voice.stop(); runCurrent()
        gate.complete(true); runCurrent()
        assertEquals(0, r.peer.starts); assertEquals(0, r.rpc.count("start_voice_chat"))
    }

    @Test fun changed_peer_after_permission_does_not_start_on_a_new_video_session() = runTest {
        val r = Rig(backgroundScope); val gate = CompletableDeferred<Boolean>()
        r.peer.permissionGate = gate
        r.voice.start(); runCurrent(); r.current = Peer()
        gate.complete(true); runCurrent()
        assertEquals(0, r.peer.starts); assertEquals(0, r.rpc.count("start_voice_chat"))
    }

    @Test fun cleanup_never_stops_another_camera_owner() = runTest {
        val r = Rig(backgroundScope); r.voice.start(); runCurrent()
        r.rpc.owner = "oem-new-preview"
        r.voice.stop(); runCurrent()
        assertFalse(r.peer.audio); assertEquals(0, r.rpc.count("stop_voice_chat"))
    }

    @Test fun native_audio_failure_cleans_robot_call_and_hides_native_error() = runTest {
        val r = Rig(backgroundScope); r.peer.nativeFailure = true
        r.voice.start(); runCurrent()
        assertEquals(1, r.rpc.count("stop_voice_chat")); assertFalse(r.peer.audio)
        assertFalse(r.voice.active.value)
        assertFalse(r.voice.error.value.orEmpty().contains("private"))
    }

    @Test fun robot_refusal_never_opens_microphone_and_does_not_expose_payload() = runTest {
        val r = Rig(backgroundScope); r.rpc.failMethod = "start_voice_chat"
        r.voice.start(); runCurrent()
        assertEquals(0, r.peer.starts); assertFalse(r.voice.active.value)
        assertFalse(r.voice.error.value.orEmpty().contains("private"))
    }

    @Test fun uncertain_cleanup_blocks_retry_on_that_video_session() = runTest {
        val r = Rig(backgroundScope); r.voice.start(); runCurrent()
        r.rpc.stopDelay = 1_000
        r.voice.stop(); runCurrent(); advanceTimeBy(501); runCurrent()
        r.voice.start(); runCurrent()
        assertEquals(1, r.rpc.count("start_voice_chat"))
        assertContains(r.voice.error.value.orEmpty(), "End video")
    }

    @Test fun speaker_volume_is_only_changed_by_explicit_valid_requests_during_owned_call() = runTest {
        val r = Rig(backgroundScope)
        r.voice.setSpeakerVolume(50); runCurrent(); assertTrue(r.rpc.calls.isEmpty())
        r.voice.start(); runCurrent(); assertNull(r.voice.speakerVolume.value)
        assertEquals(0, r.rpc.count("set_voice_chat_volume"))
        r.voice.setSpeakerVolume(-1); r.voice.setSpeakerVolume(101); runCurrent()
        assertEquals(0, r.rpc.count("set_voice_chat_volume"))
        r.voice.setSpeakerVolume(50); runCurrent()
        assertEquals(50, r.voice.speakerVolume.value)
        assertEquals(Json.parseToJsonElement("{\"volume\":50}"), r.rpc.calls.last().second)
        r.rpc.owner = "other"
        r.voice.setSpeakerVolume(75); runCurrent()
        assertEquals(1, r.rpc.count("set_voice_chat_volume")); assertEquals(50, r.voice.speakerVolume.value)
        r.voice.stop(); runCurrent()
    }
}
