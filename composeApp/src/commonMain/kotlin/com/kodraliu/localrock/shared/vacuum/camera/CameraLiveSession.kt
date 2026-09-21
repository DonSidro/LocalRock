package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.vacuum.VacuumSession
import com.kodraliu.localrock.shared.vacuum.checkHomesecPassword
import com.kodraliu.localrock.shared.vacuum.getCameraStatus
import com.kodraliu.localrock.shared.vacuum.getDeviceIce
import com.kodraliu.localrock.shared.vacuum.getDeviceSdp
import com.kodraliu.localrock.shared.vacuum.getHomesecConnectStatus
import com.kodraliu.localrock.shared.vacuum.getTurnServer
import com.kodraliu.localrock.shared.vacuum.resetHomesecPassword
import com.kodraliu.localrock.shared.vacuum.sendIceToRobot
import com.kodraliu.localrock.shared.vacuum.setCameraStatus
import com.kodraliu.localrock.shared.vacuum.setHomesecPassword
import com.kodraliu.localrock.shared.vacuum.sendSdpToRobot
import com.kodraliu.localrock.shared.vacuum.startCameraPreview
import com.kodraliu.localrock.shared.vacuum.stopCameraPreview
import com.kodraliu.localrock.shared.webrtc.RtcConnectionState
import com.kodraliu.localrock.shared.webrtc.RtcIceCandidate
import com.kodraliu.localrock.shared.webrtc.RtcIceServer
import com.kodraliu.localrock.shared.webrtc.RtcPeer
import com.kodraliu.localrock.shared.webrtc.createRtcPeer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.kotlincrypto.hash.md.MD5
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


@OptIn(ExperimentalEncodingApi::class)
class CameraLiveSession(private val session: VacuumSession) {

    sealed interface State {
        data object Idle : State
        data class Connecting(val stage: String) : State
        data object Streaming : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private val _peer = MutableStateFlow<RtcPeer?>(null)
    val peer: StateFlow<RtcPeer?> = _peer

    fun start(pin: String) {
        if (connectJob?.isActive == true) return
        _state.value = State.Connecting("Preparing…")
        connectJob = scope.launch {
            try {
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = State.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
                closePeer()
            }
        }
    }


    fun setPinAndStart(pin: String) {
        if (connectJob?.isActive == true) return
        _state.value = State.Connecting("Setting PIN on robot…")
        connectJob = scope.launch {
            try {
                val passwordMd5 = md5Hex(pin)
                val first = runCatching { session.setHomesecPassword(passwordMd5) }
                if (first.isFailure || first.getOrNull()?.error != null) {
                    runCatching { session.resetHomesecPassword() }
                    val retry = session.setHomesecPassword(passwordMd5)
                    if (retry.error != null) {
                        if (errorCode(retry.error) == ERR_PASSWORD_DISABLED) {
                            throw IllegalStateException(PASSWORD_DISABLED_HINT)
                        }
                        throw IllegalStateException("Robot refused to set the PIN: ${retry.error}")
                    }
                }
                connect(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = State.Failed(e.message ?: e::class.simpleName ?: "Unknown error")
                closePeer()
            }
        }
    }

    fun stop() {
        connectJob?.cancel()
        connectJob = null
        closePeer()
        scope.launch { runCatching { session.stopCameraPreview(CLIENT_ID) } }
        _state.value = State.Idle
    }

    fun dispose() {
        connectJob?.cancel()
        closePeer()
        val s = session
        scope.launch(NonCancellable) { runCatching { s.stopCameraPreview(CLIENT_ID) } }
            .invokeOnCompletion { scope.cancel() }
    }

    private fun closePeer() {
        _peer.value?.close()
        _peer.value = null
    }

    private suspend fun connect(pin: String) {
        val passwordMd5 = md5Hex(pin)

        _state.value = State.Connecting("Checking PIN…")
        val check = session.checkHomesecPassword(passwordMd5)
        if (check.error != null) {
            if (errorCode(check.error) == ERR_PASSWORD_DISABLED) {
                throw IllegalStateException(PASSWORD_DISABLED_HINT)
            }
            throw IllegalStateException("PIN check failed: ${check.error}")
        }
        val ok = firstInt(check.result)
        if (ok != null && ok != 1) throw IllegalStateException("Wrong camera PIN")

        // If another client (e.g. the official app) holds the camera, ask it to let go.
        _state.value = State.Connecting("Claiming camera…")
        var kicks = 0
        while (kicks < 5) {
            val status = runCatching { session.getHomesecConnectStatus() }.getOrNull()
            val other = firstObject(status?.result)?.get("client_id")?.jsonPrimitive?.contentOrNull
            // The robot reports the literal "none" when no client holds the camera. Treating that
            // as a rival made us fire stop_camera_preview five times before every start.
            if (other.isNullOrBlank() || other == NO_CLIENT || other == CLIENT_ID) break
            runCatching { session.stopCameraPreview(other) }
            delay(1_000)
            kicks++
        }

        _state.value = State.Connecting("Starting camera…")
        val startResp = session.startCameraPreview(CLIENT_ID, passwordMd5)
        if (startResp.error != null) {
            if (errorCode(startResp.error) == ERR_CAMERA_PREVIEW_DENIED) {
                val status = runCatching { firstInt(session.getCameraStatus().result) }.getOrNull()
                throw IllegalStateException(
                    "Remote viewing is not enabled on this robot (-10012, camera_status=" +
                        "${status ?: "unknown"}). It can only be enabled from the official Roborock " +
                        "app while the robot is bound to Roborock's cloud, and the setting is lost " +
                        "when the robot is moved back to a local server."
                )
            }
            throw IllegalStateException("Robot refused camera preview: ${startResp.error}")
        }

        _state.value = State.Connecting("Negotiating…")
        val iceServers = mutableListOf<RtcIceServer>()
        runCatching {
            val turn = firstObject(session.getTurnServer().result) ?: return@runCatching
            val url = turn["url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            iceServers += RtcIceServer(
                url = url,
                username = turn["user"]?.jsonPrimitive?.contentOrNull ?: "",
                password = turn["pwd"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }

        val peer = createRtcPeer(iceServers)
        _peer.value = peer

        coroutineScope {
            val localIceJob = launch {
                peer.localCandidates.collect { c ->
                    val json = buildJsonObject {
                        put("candidate", c.candidate)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("sdpMid", c.sdpMid ?: "0")
                    }
                    runCatching { session.sendIceToRobot(b64(json)) }
                }
            }

            val offerSdp = peer.createOffer()
            val offerJson = buildJsonObject {
                put("type", "offer")
                put("sdp", offerSdp)
            }
            session.sendSdpToRobot(b64(offerJson))

            var answerSdp: String? = null
            var tries = 0
            while (answerSdp == null && tries < 15) {
                val devSdp = firstObject(runCatching { session.getDeviceSdp() }.getOrNull()?.result)
                    ?.get("dev_sdp")?.jsonPrimitive?.contentOrNull
                if (!devSdp.isNullOrBlank() && devSdp != "retry") {
                    val decoded = runCatching { Base64.decode(devSdp).decodeToString() }.getOrDefault(devSdp)
                    answerSdp = runCatching {
                        SignalingJson.parseToJsonElement(decoded).jsonObject["sdp"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                }
                if (answerSdp == null) {
                    delay(1_000)
                    tries++
                }
            }
            if (answerSdp == null) {
                localIceJob.cancel()
                throw IllegalStateException("Robot did not answer — does this model have a camera?")
            }
            peer.setRemoteAnswer(answerSdp)

            val remoteIceJob = launch {
                val seen = mutableSetOf<String>()
                repeat(30) {
                    val result = runCatching { session.getDeviceIce() }.getOrNull()?.result
                    val entries = firstObject(result)?.get("dev_ice") as? JsonArray
                    entries?.forEach { entry ->
                        val raw = (entry as? JsonPrimitive)?.contentOrNull ?: return@forEach
                        if (!seen.add(raw)) return@forEach
                        val decoded = runCatching { Base64.decode(raw).decodeToString() }.getOrNull() ?: return@forEach
                        runCatching {
                            val o = SignalingJson.parseToJsonElement(decoded).jsonObject
                            peer.addRemoteCandidate(
                                RtcIceCandidate(
                                    sdpMid = o["sdpMid"]?.jsonPrimitive?.contentOrNull,
                                    sdpMLineIndex = o["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                                    candidate = o["candidate"]?.jsonPrimitive?.contentOrNull ?: return@runCatching,
                                )
                            )
                        }
                    }
                    if (peer.connectionState.value == RtcConnectionState.CONNECTED) return@launch
                    delay(1_000)
                }
            }

            peer.connectionState.collect { st ->
                when (st) {
                    RtcConnectionState.CONNECTED -> {
                        _state.value = State.Streaming
                        localIceJob.cancel()
                        remoteIceJob.cancel()
                    }
                    RtcConnectionState.FAILED ->
                        throw IllegalStateException("Video connection failed")
                    RtcConnectionState.DISCONNECTED ->
                        if (_state.value is State.Streaming) throw IllegalStateException("Video connection lost")
                    else -> Unit
                }
            }
        }
    }

    private fun b64(json: JsonElement): String =
        Base64.encode(SignalingJson.encodeToString(JsonElement.serializer(), json).encodeToByteArray())

    private companion object {
        const val CLIENT_ID = "7661636c6f63616c"
        val SignalingJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

        /** Robot error for "password is disable": the homesec feature is off on the device. */
        const val ERR_PASSWORD_DISABLED = -1007

        /** Robot error for "camera status denied preview": the camera flag forbids streaming. */
        const val ERR_CAMERA_PREVIEW_DENIED = -10012

        /**
         * `camera_status` findings for a Qrevo Curv 2 Flow (2026-09-21), kept because they are not
         * documented anywhere: 259 (0x103) with remote viewing off, 8615 (0x21A7) when Roborock's
         * cloud enables it. **Bit 2 (0x4) is the authorization and is write-protected** —
         * `set_camera_status` rejects any value containing it with -10005, while bits 5, 7 and 13
         * write fine and do not unblock the preview. No RPC on this firmware sets `home_sec_status`.
         */
        const val CAMERA_STATUS_OFF = 259

        /** `client_id` the robot returns when the camera is free. */
        const val NO_CLIENT = "none"

        const val PASSWORD_DISABLED_HINT =
            "This robot reports the camera PIN feature is disabled (-1007). Enable remote viewing " +
                "for this vacuum in the official Roborock app once, then try again here."

        private fun errorCode(error: JsonObject?): Int? =
            error?.get("code")?.jsonPrimitive?.intOrNull

        fun md5Hex(input: String): String {
            val digest = MD5().digest(input.encodeToByteArray())
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                val v = b.toInt() and 0xff
                sb.append("0123456789abcdef"[v ushr 4])
                sb.append("0123456789abcdef"[v and 0x0f])
            }
            return sb.toString()
        }

        fun firstObject(result: JsonElement?): JsonObject? = when (result) {
            is JsonObject -> result
            is JsonArray -> result.firstOrNull() as? JsonObject
            else -> null
        }

        fun firstInt(result: JsonElement?): Int? = when (result) {
            is JsonPrimitive -> result.intOrNull
            is JsonArray -> (result.firstOrNull() as? JsonPrimitive)?.intOrNull
            else -> null
        }
    }
}
