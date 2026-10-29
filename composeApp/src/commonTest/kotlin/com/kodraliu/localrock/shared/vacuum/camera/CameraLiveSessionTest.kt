package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.webrtc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalEncodingApi::class)
class CameraLiveSessionTest {
    private class FakePeer : RtcPeer {
        override val localCandidates = MutableSharedFlow<RtcIceCandidate>(replay = 4)
        override val connectionState = MutableStateFlow(RtcConnectionState.NEW)
        var closed = false
        var receivedVideo = true
        var rejectAnswer = false
        var answerApplied = false
        override val supportsVoiceCall = true
        var audioActive = false
        override suspend fun requestMicrophonePermission() = true
        override suspend fun startVoiceCall() { audioActive = true }
        override fun stopVoiceCall() { audioActive = false }
        val remoteCandidates = mutableListOf<RtcIceCandidate>()
        var offerOptions: RtcOfferOptions? = null
        override suspend fun createOffer(options: RtcOfferOptions): String {
            offerOptions = options
            localCandidates.emit(RtcIceCandidate("0", 0, "candidate:test"))
            return "test-offer"
        }
        override suspend fun setRemoteAnswer(sdp: String) {
            if (rejectAnswer) error("private-native-sdp")
            answerApplied = true
            connectionState.value = RtcConnectionState.CONNECTED
        }
        override suspend fun addRemoteCandidate(candidate: RtcIceCandidate) {
            check(answerApplied) { "Candidate applied before answer" }
            remoteCandidates += candidate
        }
        override suspend fun diagnostics() = selectedPairDiagnostics("host", "srflx", "udp", receivedVideo)
        override fun close() { closed = true; connectionState.value = RtcConnectionState.CLOSED }
    }

    private class FakeRpc : CameraRpc {
        val calls = mutableListOf<String>()
        val stoppedIds = mutableListOf<String>()
        val qualities = mutableListOf<String>()
        val offers = mutableListOf<String>()
        val qualityChanges = mutableListOf<JsonElement>()
        var qualityDelay = 0L
        var voiceStopDelay = 0L
        var pinResult: JsonElement? = JsonPrimitive(true)
        var owner: String? = "none"
        var startDelay: Long = 0
        var waitForAnswer = false
        var malformedAnswer = false
        var failMethod: String? = null
        var failCode = -10005
        var failResult: JsonElement? = null
        var cleanupUnavailable = false
        var candidates: JsonElement = JsonArray(emptyList())
        override suspend fun call(method: String, params: JsonElement): V1Response {
            calls += method
            if (method == failMethod) return V1Response(null, failResult, buildJsonObject {
                put("code", failCode); put("message", "private-robot-payload")
            })
            val result = when (method) {
                "check_homesec_password" -> pinResult
                "get_homesec_connect_status" -> {
                    if (cleanupUnavailable) error("private-network-error")
                    owner?.let { buildJsonObject { put("client_id", it) } } ?: JsonObject(emptyMap())
                }
                "start_camera_preview" -> {
                    qualities += params.jsonObject["quality"]!!.jsonPrimitive.content
                    delay(startDelay)
                    owner = params.jsonObject["client_id"]!!.jsonPrimitive.content
                    JsonArray(listOf(JsonPrimitive("ok")))
                }
                "stop_camera_preview" -> {
                    stoppedIds += params.jsonObject["client_id"]!!.jsonPrimitive.content
                    owner = "none"
                    JsonArray(listOf(JsonPrimitive("ok")))
                }
                "switch_video_quality" -> {
                    qualityChanges += params
                    delay(qualityDelay)
                    JsonArray(listOf(JsonPrimitive("ok")))
                }
                "stop_voice_chat" -> {
                    delay(voiceStopDelay)
                    JsonArray(listOf(JsonPrimitive("ok")))
                }
                "get_turn_server" -> buildJsonObject {
                    put("url", "turn:example.test:3478"); put("user", "test"); put("pwd", "test")
                }
                "get_device_sdp" -> buildJsonObject {
                    put("dev_sdp", when {
                        waitForAnswer -> "retry"
                        malformedAnswer -> "invalid-base64"
                        else -> Base64.encode("{\"type\":\"answer\",\"sdp\":\"test-answer\"}".encodeToByteArray())
                    })
                }
                "get_device_ice" -> buildJsonObject { put("dev_ice", candidates) }
                "send_sdp_to_robot" -> {
                    offers += Base64.decode(params.jsonObject["app_sdp"]!!.jsonPrimitive.content).decodeToString()
                    JsonArray(listOf(JsonPrimitive("ok")))
                }
                else -> JsonArray(listOf(JsonPrimitive("ok")))
            }
            return V1Response(null, result, null)
        }
    }

    private fun TestScope.live(rpc: FakeRpc, peer: FakePeer = FakePeer()): CameraLiveSession {
        var id = 0
        return CameraLiveSession(rpc, { peer },
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])),
            clientId = { "test-client-${++id}" }, connectTimeoutMs = 5_000, cleanupTimeoutMs = 500)
    }

    @Test fun ending_video_stops_audio_before_releasing_preview_and_waits_for_call_cleanup() = runTest {
        val rpc = FakeRpc().apply { voiceStopDelay = 300 }
        val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        live.voice.start(); runCurrent()
        assertTrue(peer.audioActive); assertTrue(live.voice.active.value)
        live.stop()
        assertFalse(peer.audioActive)
        runCurrent()
        assertFalse(peer.closed); assertTrue(rpc.stoppedIds.isEmpty())
        advanceTimeBy(301); runCurrent()
        assertTrue(peer.closed)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        assertTrue(rpc.calls.indexOf("stop_voice_chat") < rpc.calls.indexOf("stop_camera_preview"))
        assertFalse(live.voice.active.value)
        live.dispose()
    }

    @Test fun connection_loss_releases_the_active_call_and_preview() = runTest {
        val rpc = FakeRpc(); val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent(); live.voice.start(); runCurrent()
        assertTrue(peer.audioActive)
        peer.connectionState.value = RtcConnectionState.DISCONNECTED; runCurrent()
        assertFalse(peer.audioActive); assertFalse(live.voice.active.value)
        assertEquals(1, rpc.calls.count { it == "stop_voice_chat" })
        assertTrue(peer.closed); assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun all_four_tests_send_the_selected_offer_and_preview_quality_and_release_their_camera() = runTest(timeout = 10.seconds) {
        for (test in CameraVideoTest.entries) {
            val rpc = FakeRpc()
            val peer = FakePeer()
            val live = live(rpc, peer)
            live.start("1234", test); runCurrent()
            assertEquals(test.offerOptions, peer.offerOptions, test.code)
            assertEquals(listOf(test.previewQuality), rpc.qualities, test.code)
            assertIs<CameraLiveSession.State.Streaming>(live.state.value)
            assertTrue("get_turn_server" in rpc.calls)
            live.stop(); runCurrent()
            assertTrue(peer.closed)
            assertEquals(listOf("test-client-1"), rpc.stoppedIds)
            live.dispose()
        }
    }

    @Test fun selecting_another_test_cannot_overlap_an_attempt_or_change_its_offer() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { startDelay = 300 }
        val peers = mutableListOf<FakePeer>()
        var id = 0
        val live = CameraLiveSession(rpc, { FakePeer().also { peers += it } },
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job])),
            clientId = { "variant-client-${++id}" }, connectTimeoutMs = 5_000, cleanupTimeoutMs = 500)
        live.start("1234", CameraVideoTest.ESCAPED_JSON); runCurrent()
        live.start("1234", CameraVideoTest.COMPACT_CODECS)
        live.stop(); runCurrent()
        live.start("1234", CameraVideoTest.COMPACT_CODECS)
        advanceTimeBy(301); runCurrent()
        assertEquals(listOf("SD"), rpc.qualities)
        assertEquals(listOf("variant-client-1"), rpc.stoppedIds)
        assertTrue(peers.isEmpty())
        rpc.startDelay = 0
        live.start("1234", CameraVideoTest.COMPACT_CODECS); runCurrent()
        assertEquals(listOf("SD", "SD"), rpc.qualities)
        assertEquals(CameraVideoTest.COMPACT_CODECS.offerOptions, peers.single().offerOptions)
        live.stop(); runCurrent()
        assertEquals(listOf("variant-client-1", "variant-client-2"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun variants_each_change_one_setting_from_the_oem_match() {
        val a = CameraVideoTest.OEM_MATCH
        val b = CameraVideoTest.ESCAPED_JSON
        val c = CameraVideoTest.COMPACT_CODECS
        val d = CameraVideoTest.ANSWER_FIRST_ICE
        assertEquals(RtcOfferOptions(), a.offerOptions)
        assertEquals(a.offerOptions, b.offerOptions)
        assertEquals(a.offerOptions.copy(compactCodecs = true), c.offerOptions)
        assertEquals(a.offerOptions, d.offerOptions)
        assertEquals(listOf(true, false, true, true), listOf(a,b,c,d).map { it.literalSdpLineBreaks })
        assertEquals(listOf(true, true, true, false), listOf(a,b,c,d).map { it.earlyRemoteIce })
        assertEquals(listOf("SD", "SD", "SD", "SD"), listOf(a,b,c,d).map { it.previewQuality })
    }

    private fun candidate(text: String) = JsonPrimitive(Base64.encode(buildJsonObject {
        put("candidate", text); put("sdpMid", "1"); put("sdpMLineIndex", 1)
    }.toString().encodeToByteArray()))

    @Test fun early_ice_is_polled_during_retries_but_applied_only_after_answer_and_once() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply {
            waitForAnswer = true
            candidates = JsonArray(listOf(candidate("candidate:first"), candidate("candidate:second")))
        }
        val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent(); advanceTimeBy(2_001); runCurrent()
        assertTrue(rpc.calls.count { it == "get_device_ice" } >= 2)
        assertTrue(peer.remoteCandidates.isEmpty())
        rpc.waitForAnswer = false
        advanceTimeBy(1_001); runCurrent()
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        assertEquals(listOf("candidate:first", "candidate:second"), peer.remoteCandidates.map { it.candidate })
        assertTrue(peer.remoteCandidates.all { it.sdpMid == "1" && it.sdpMLineIndex == 1 })
        live.stop(); runCurrent()
        val count = rpc.calls.size
        advanceTimeBy(10_000); runCurrent()
        assertEquals(count, rpc.calls.size)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun answer_first_variant_defers_ice_polling_until_answer() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { waitForAnswer = true }
        val live = live(rpc)
        live.start("1234", CameraVideoTest.ANSWER_FIRST_ICE); runCurrent(); advanceTimeBy(1_001); runCurrent()
        assertFalse("get_device_ice" in rpc.calls)
        rpc.waitForAnswer = false
        advanceTimeBy(1_001); runCurrent()
        assertTrue("get_device_ice" in rpc.calls)
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        live.dispose(); runCurrent()
    }

    @Test fun cancelling_while_candidates_are_buffered_releases_without_applying_them() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply {
            waitForAnswer = true
            candidates = JsonArray((0..70).map { candidate("candidate:$it") })
        }
        val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        assertTrue("get_device_ice" in rpc.calls)
        live.stop(); runCurrent()
        assertTrue(peer.closed)
        assertTrue(peer.remoteCandidates.isEmpty())
        assertIs<CameraLiveSession.State.Idle>(live.state.value)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun default_client_identifier_matches_oem_length_and_is_fresh() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc()
        val live = CameraLiveSession(rpc, { FakePeer() }, backgroundScope, connectTimeoutMs = 5_000)
        repeat(3) { live.start("1234"); runCurrent(); live.stop(); runCurrent() }
        assertEquals(3, rpc.stoppedIds.toSet().size)
        assertTrue(rpc.stoppedIds.all { it.matches(Regex("[0-9a-f]{8}")) })
        live.dispose(); runCurrent()
    }

    @Test fun onlyExplicitSuccessfulPinRepliesPass() {
        for (value in listOf("true", "1", "[true]", "[1]", "[\"ok\"]")) assertTrue(cameraPinAccepted(Json.parseToJsonElement(value)))
        for (value in listOf("false", "0", "[false]", "[0]", "null", "{}", "[]", "[1,1]", "\"true\"", "\"1\"",
            "\"ok\"", "[\"OK\"]", "[\"ok\",\"ok\"]", "[\"ok\",false]", "{\"result\":[\"ok\"]}", "[\"error\"]")) {
            assertFalse(cameraPinAccepted(Json.parseToJsonElement(value)), value)
        }
        assertFalse(cameraPinAccepted(null))
    }

    @Test fun s7MaxvPinAcknowledgementStartsAndReleasesOwnedPreview() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { pinResult = Json.parseToJsonElement("[\"ok\"]") }
        val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        assertEquals(listOf("check_homesec_password", "get_homesec_connect_status", "start_camera_preview"), rpc.calls.take(3))
        assertTrue("get_turn_server" in rpc.calls)
        live.stop(); runCurrent()
        assertTrue(peer.closed)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun pinErrorCannotBeOverriddenByOkResult() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply {
            failMethod = "check_homesec_password"
            failResult = Json.parseToJsonElement("[\"ok\"]")
        }
        val live = live(rpc)
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Failed>(live.state.value)
        assertEquals(listOf("check_homesec_password"), rpc.calls)
        live.dispose(); runCurrent()
        assertTrue(rpc.stoppedIds.isEmpty())
    }

    @Test fun invalidPinNeverStartsOrStopsPreview() = runTest(timeout = 10.seconds) {
        for (result in listOf(JsonPrimitive(false), JsonNull, JsonObject(emptyMap()))) {
            val rpc = FakeRpc().apply { pinResult = result }
            val live = live(rpc)
            live.start("1234"); runCurrent()
            assertIs<CameraLiveSession.State.Failed>(live.state.value)
            assertEquals(listOf("check_homesec_password"), rpc.calls)
            live.dispose(); runCurrent()
        }
    }

    @Test fun entry_verification_does_not_start_preview_or_allocate_a_peer() = runTest {
        val rpc=FakeRpc();val peer=FakePeer();val live=live(rpc,peer)
        live.checkCredential("1234")
        assertEquals(listOf("check_homesec_password"),rpc.calls)
        assertEquals(CameraLiveSession.State.Idle,live.state.value)
        assertNull(live.peer.value);assertNull(peer.offerOptions);assertFalse(peer.audioActive)
        live.dispose();runCurrent()
        assertEquals(listOf("check_homesec_password"),rpc.calls)
    }

    @Test fun entry_verification_rejects_wrong_or_unrecognized_credentials_without_preview() = runTest {
        for(result in listOf(JsonPrimitive(false),JsonObject(emptyMap()))) {
            val rpc=FakeRpc().apply {pinResult=result};val live=live(rpc)
            val failure=assertFailsWith<CameraFailure> {live.checkCredential("1234")}
            assertContains(failure.message!!,if(result==JsonPrimitive(false)) "rejected" else "unrecognized")
            assertEquals(listOf("check_homesec_password"),rpc.calls)
            assertNull(live.peer.value);assertEquals(CameraLiveSession.State.Idle,live.state.value)
            live.dispose();runCurrent()
        }
    }

    @Test fun unexpected_pin_reply_is_not_reported_as_a_wrong_credential() = runTest(timeout = 10.seconds) {
        for (result in listOf(JsonPrimitive(false), JsonObject(emptyMap()))) {
            val rpc = FakeRpc().apply { pinResult = result }
            val live = live(rpc)
            live.start("1234"); runCurrent()
            val message = assertIs<CameraLiveSession.State.Failed>(live.state.value).message
            assertContains(message, if (result == JsonPrimitive(false)) "rejected" else "unrecognized")
            assertEquals(listOf("check_homesec_password"), rpc.calls)
            live.dispose(); runCurrent()
        }
    }

    @Test fun busyCameraIsNotTakenOverOrStoppedOnDispose() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { owner = "oem-client" }
        val live = live(rpc)
        live.start("1234"); runCurrent()
        assertContains(assertIs<CameraLiveSession.State.Failed>(live.state.value).message, "Camera in use")
        live.dispose(); runCurrent()
        assertFalse("start_camera_preview" in rpc.calls)
        assertTrue(rpc.stoppedIds.isEmpty())
        assertEquals("oem-client", rpc.owner)
    }

    @Test fun unknownOwnershipFailsClosed() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { owner = null }
        val live = live(rpc)
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Failed>(live.state.value)
        assertFalse("start_camera_preview" in rpc.calls)
        live.dispose()
    }

    @Test fun streamingUsesTurnAndStopsOnlyOwnedPreview() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        assertEquals(RtcMediaPath.DIRECT, live.diagnostics.value.path)
        assertTrue("get_turn_server" in rpc.calls)
        assertTrue(rpc.calls.indexOf("send_sdp_to_robot") < rpc.calls.indexOf("send_ice_to_robot"))
        live.stop(); runCurrent()
        assertTrue(peer.closed)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        assertIs<CameraLiveSession.State.Idle>(live.state.value)
        assertNull(live.cleanupWarning.value)
        live.dispose()
    }

    @Test fun cleanupDoesNotStopNewOwner() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val live = live(rpc)
        live.start("1234"); runCurrent(); rpc.owner = "oem-new-client"
        live.dispose(); runCurrent()
        assertTrue(rpc.stoppedIds.isEmpty())
        assertEquals("oem-new-client", rpc.owner)
    }

    @Test fun cancellationDuringStartWaitsThenReleasesAndBlocksRetry() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { startDelay = 2_000 }; val live = live(rpc)
        live.start("1234"); runCurrent()
        live.stop(); live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Stopping>(live.state.value)
        advanceTimeBy(2_001); runCurrent()
        assertEquals(1, rpc.calls.count { it == "start_camera_preview" })
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        assertFalse("get_turn_server" in rpc.calls)
        assertIs<CameraLiveSession.State.Idle>(live.state.value)
        live.dispose()
    }

    @Test fun connectionTimeoutReleasesPreview() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { waitForAnswer = true }; val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent(); advanceTimeBy(5_001); runCurrent()
        assertContains(assertIs<CameraLiveSession.State.Failed>(live.state.value).message, "timed out")
        assertTrue(peer.closed)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun handshakeWithoutFramesDoesNotBecomeLive() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val peer = FakePeer().apply { receivedVideo = false }; val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Connecting>(live.state.value)
        advanceTimeBy(5_001); runCurrent()
        assertIs<CameraLiveSession.State.Failed>(live.state.value)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun signalingRefusalReleasesAndDoesNotExposePayload() = runTest(timeout = 10.seconds) {
        for (method in listOf("get_turn_server", "send_sdp_to_robot", "send_ice_to_robot", "get_device_ice")) {
            val rpc = FakeRpc().apply { failMethod = method }; val live = live(rpc)
            live.start("1234"); runCurrent()
            val failed = assertIs<CameraLiveSession.State.Failed>(live.state.value)
            assertFalse(failed.message.contains("private-robot-payload"))
            assertContains(failed.message, method)
            assertEquals(listOf("test-client-1"), rpc.stoppedIds)
            live.dispose()
        }
    }

    @Test fun nativeAnswerErrorIsSanitizedAndReleases() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val peer = FakePeer().apply { rejectAnswer = true }; val live = live(rpc, peer)
        live.start("1234"); runCurrent()
        val failed = assertIs<CameraLiveSession.State.Failed>(live.state.value)
        assertFalse(failed.message.contains("private-native-sdp"))
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun malformedSignalingIsReportedAndReleased() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { malformedAnswer = true }; val live = live(rpc)
        live.start("1234"); runCurrent()
        assertContains(assertIs<CameraLiveSession.State.Failed>(live.state.value).message, "invalid video answer")
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun activationDenialNeverChangesSettingsOrPin() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc().apply { failMethod = "start_camera_preview"; failCode = -10012 }; val live = live(rpc)
        live.start("1234"); runCurrent()
        val text = assertIs<CameraLiveSession.State.Failed>(live.state.value).message
        assertFalse(text.contains("cloud"))
        assertFalse(rpc.calls.any { it.startsWith("set_") || it.startsWith("reset_") })
        assertTrue(rpc.stoppedIds.isEmpty())
        live.dispose()
    }

    @Test fun failedCleanupIsVisibleAndNeverStopsUnknownClient() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val live = live(rpc)
        live.start("1234"); runCurrent(); rpc.cleanupUnavailable = true
        live.stop(); runCurrent()
        assertNotNull(live.cleanupWarning.value)
        assertTrue(rpc.stoppedIds.isEmpty())
        assertIs<CameraLiveSession.State.Idle>(live.state.value)
        live.dispose()
    }

    @Test fun retryUsesFreshClientIdAndDoesNotOverlapPreviousAttempt() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val live = live(rpc)
        live.start("1234"); live.start("1234"); runCurrent()
        live.stop(); runCurrent()
        live.start("1234"); runCurrent(); live.stop(); runCurrent()
        assertEquals(listOf("test-client-1", "test-client-2"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun connectionLossClosesPeerAndReleases() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val peer = FakePeer(); val live = live(rpc, peer)
        live.start("1234"); runCurrent(); peer.connectionState.value = RtcConnectionState.DISCONNECTED; runCurrent()
        assertIs<CameraLiveSession.State.Failed>(live.state.value)
        assertTrue(peer.closed)
        assertEquals(listOf("test-client-1"), rpc.stoppedIds)
        live.dispose()
    }

    @Test fun immediateCancellationBeforeDispatchCanBeRetried() = runTest(timeout = 10.seconds) {
        val rpc = FakeRpc(); val live = live(rpc)
        live.start("1234"); live.stop(); runCurrent()
        assertIs<CameraLiveSession.State.Idle>(live.state.value)
        assertTrue(rpc.calls.isEmpty())
        live.start("1234"); runCurrent()
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        live.dispose(); runCurrent()
    }

    @Test fun onlyS7MaxVGetsExperimentalEntry() {
        assertTrue(experimentalCameraSupported("roborock.vacuum.a27"))
        for (model in listOf(null, "", "roborock.vacuum.a15", "roborock.vacuum.a70")) assertFalse(experimentalCameraSupported(model))
    }

    @Test fun selectedPairMetadataIsSanitizedAndDoesNotGuess() {
        assertEquals(RtcMediaPath.UNKNOWN, selectedPairDiagnostics(null, null, "udp", false).path)
        assertEquals(RtcMediaPath.UNKNOWN, selectedPairDiagnostics("host", "private-data", "private-data", true).path)
        assertEquals(RtcMediaPath.DIRECT, selectedPairDiagnostics("host", "srflx", "udp", true).path)
        assertEquals(RtcMediaPath.RELAY, selectedPairDiagnostics("relay", "host", "udp", true).path)
        assertEquals(RtcMediaPath.RELAY, selectedPairDiagnostics("host", "relay", "tcp", true).path)
        assertFalse(selectedPairDiagnostics("host", "private-data", "private-data", true).toString().contains("private-data"))
    }
    @Test fun hd_start_keeps_oem_signaling_and_releases_its_preview() = runTest {
        val rpc=FakeRpc();val peer=FakePeer();val live=live(rpc,peer)
        live.start("1234",CameraVideoTest.OEM_MATCH,CameraVideoQuality.HD);runCurrent()
        assertEquals(listOf("HD"),rpc.qualities)
        assertEquals(RtcOfferOptions(),peer.offerOptions)
        assertEquals(CameraVideoQuality.HD,live.quality.value)
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        live.stop();runCurrent();assertEquals(listOf("test-client-1"),rpc.stoppedIds);live.dispose()
    }
    @Test fun quality_switch_reuses_peer_and_preserves_the_stream() = runTest {
        val rpc=FakeRpc();val peer=FakePeer();val live=live(rpc,peer)
        live.start("1234");runCurrent()
        live.changeQuality(CameraVideoQuality.HD);runCurrent()
        assertEquals(CameraVideoQuality.HD,live.quality.value)
        live.changeQuality(CameraVideoQuality.SD);runCurrent()
        assertEquals(listOf("{\"quality\":\"HD\"}","{\"quality\":\"SD\"}"),rpc.qualityChanges.map {it.toString()})
        assertSame(peer,live.peer.value);assertFalse(peer.closed);assertEquals(1,rpc.offers.size)
        assertEquals(1,rpc.qualities.size);assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        live.stop();runCurrent();live.dispose()
    }
    @Test fun refused_quality_change_retains_last_confirmed_setting() = runTest {
        val rpc=FakeRpc();val live=live(rpc)
        live.start("1234");runCurrent();rpc.failMethod="switch_video_quality"
        live.changeQuality(CameraVideoQuality.HD);runCurrent()
        assertEquals(CameraVideoQuality.SD,live.quality.value);assertNotNull(live.qualityError.value)
        assertFalse(live.qualityError.value!!.contains("private"));assertFalse(live.qualityBusy.value)
        assertIs<CameraLiveSession.State.Streaming>(live.state.value)
        live.stop();runCurrent();live.dispose()
    }
    @Test fun another_viewer_prevents_quality_commands() = runTest {
        val rpc=FakeRpc();val live=live(rpc)
        live.start("1234");runCurrent();rpc.owner="another-viewer"
        live.changeQuality(CameraVideoQuality.HD);runCurrent()
        assertTrue(rpc.qualityChanges.isEmpty());assertNotNull(live.qualityError.value)
        live.stop();runCurrent();assertTrue(rpc.stoppedIds.isEmpty());live.dispose()
    }
    @Test fun pending_quality_change_does_not_overlap_or_restart_video() = runTest {
        val rpc=FakeRpc().apply {qualityDelay=300};val live=live(rpc)
        live.start("1234");runCurrent();live.changeQuality(CameraVideoQuality.HD);runCurrent()
        live.changeQuality(CameraVideoQuality.HD);live.changeQuality(CameraVideoQuality.SD)
        assertTrue(live.qualityBusy.value);advanceTimeBy(301);runCurrent()
        assertEquals(1,rpc.qualityChanges.size);assertEquals(1,rpc.qualities.size)
        assertEquals(CameraVideoQuality.HD,live.quality.value)
        live.stop();runCurrent();live.dispose()
    }
    @Test fun stopping_cancels_pending_quality_change() = runTest {
        val rpc=FakeRpc().apply {qualityDelay=300};val live=live(rpc)
        live.start("1234");runCurrent();live.changeQuality(CameraVideoQuality.HD);runCurrent()
        live.stop();runCurrent();advanceTimeBy(301);runCurrent()
        assertEquals(CameraVideoQuality.SD,live.quality.value);assertFalse(live.qualityBusy.value)
        val count=rpc.calls.size;live.changeQuality(CameraVideoQuality.HD);runCurrent();assertEquals(count,rpc.calls.size)
        live.dispose()
    }

}

