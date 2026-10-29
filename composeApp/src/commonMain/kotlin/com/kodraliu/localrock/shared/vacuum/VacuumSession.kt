package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.mqtt.MqttClient
import com.kodraliu.localrock.shared.mqtt.MqttTopics
import com.kodraliu.localrock.shared.protocol.RoborockCodec
import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.protocol.V1Envelope
import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

class VacuumException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The robot sent nothing at all for a command, not even the `["retry"]` it sends while it works. */
class RobotSilentException(val method: String) : RuntimeException("The robot didn't answer $method")


class VacuumSession(
    private val localKey: String,
    private val mqtt: MqttClient,
    private val topicsProvider: () -> MqttTopics,
    private val nowEpochSeconds: () -> Int,
    private val random: Random = Random.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val demo: Boolean = false,
    /**
     * Ids this session draws from. Two sessions listening to the same robot must use ranges that
     * do not overlap, or a reply meant for one could complete a request of the other.
     */
    private val requestIds: IntRange = PRIMARY_REQUEST_IDS,
) {
    // In demo mode the session performs no MQTT I/O: start/close are no-ops and every command
    // resolves to an empty response, so the repository's control methods become harmless no-ops.
    private val emptyResponse = V1Response(id = null, result = null, error = null)
    private val mutex = Mutex()
    private var nextId = random.nextInt(requestIds.first, requestIds.last + 1)
    private val pending = mutableMapOf<Int, PendingRequest>()

    private class PendingRequest {
        val answer = CompletableDeferred<V1Response>()
        /** Completed by the first reply of any kind, the provisional `["retry"]` included. */
        val heard = CompletableDeferred<Unit>()
    }

    /** The params shape this robot takes for map commands (see [sendDataCommand]). */
    internal val dataParamsFormat = DataParamsFormat()
    private val _dpsUpdates = MutableStateFlow<Map<Int, JsonElement>>(emptyMap())
    val dpsUpdates: StateFlow<Map<Int, JsonElement>> = _dpsUpdates
    private var collectorJob: Job? = null
    private var security: SecurityData? = null
    private var pendingMap: CompletableDeferred<MapResponse>? = null
    /** Set while [pendingMap] is a saved-map request: only the frame answering this id completes it. */
    private var pendingMapId: Int? = null
    /** Saved-map requests sent so far. A late frame for one of them must never pass for the live map. */
    private val savedMapRequestIds = mutableSetOf<Int>()
    /** Photos asked for with get_photo, by request id. */
    private val pendingBlobs = mutableMapOf<Int, CompletableDeferred<ByteArray>>()

    /** The topic we are actually subscribed to, which can lag [topicsProvider] after a re-login. */
    private var subscribedTopic: String? = null

    private val topics: MqttTopics get() = topicsProvider()

    suspend fun start() {
        if (demo) return
        check(collectorJob == null) { "Session already started" }
        val topic = topics.subscribeTopic
        mqtt.subscribe(topic)
        subscribedTopic = topic
        collectorJob = scope.launch {
            mqtt.messages.collect { msg ->
                if (msg.topic == subscribedTopic) {
                    println("[VacLocal] mqtt in: topic=${msg.topic} bytes=${msg.payload.size}")
                    handleIncoming(msg.payload)
                } else {
                    println("[VacLocal] mqtt in (OTHER topic, ignored): ${msg.topic}")
                }
            }
        }
    }

    suspend fun sendCommand(
        method: String,
        params: List<JsonElement> = emptyList(),
        timeoutMs: Long = 10_000L,
    ): V1Response = sendCommandRaw(method, JsonArray(params), timeoutMs)

    /**
     * With [firstReplyTimeoutMs] the robot must send something for this request within that time,
     * the provisional `["retry"]` counts, or [RobotSilentException] is thrown.
     */
    suspend fun sendCommandRaw(
        method: String,
        params: JsonElement,
        timeoutMs: Long = 10_000L,
        firstReplyTimeoutMs: Long? = null,
    ): V1Response {
        if (demo) return emptyResponse
        return sendRequest(nextRequestId(), method, params, timeoutMs, firstReplyTimeoutMs)
    }

    /**
     * Fetches an obstacle photo and returns the image (#36). The robot answers get_photo with
     * `["ok"]` and sends the photo afterwards as a frame of its own carrying the request id, so the
     * photo is waited for under that id from before the request goes out.
     */
    suspend fun fetchPhoto(security: SecurityData, photoId: String, timeoutMs: Long = 15_000L): ByteArray {
        check(!demo && collectorJob != null) { "Session must be started before fetchPhoto" }
        val keyAnswer = sendCommand("get_random_pkey").requireOk("get_random_pkey").result
        val publicKey = ((keyAnswer as? JsonArray)?.firstOrNull() ?: keyAnswer)
            .let { (it as? JsonObject)?.get("pub_key") as? JsonObject }
            ?: throw RoborockProtocolException("get_random_pkey did not return a public key")
        val requestId = nextRequestId()
        val photo = CompletableDeferred<ByteArray>()
        mutex.withLock { pendingBlobs[requestId] = photo }
        try {
            val securityJson = buildJsonObject {
                put("endpoint", security.endpoint)
                put("nonce", security.nonceHex)
            }
            sendRequest(
                requestId, "get_photo", getPhotoParams(publicKey, security, photoId), timeoutMs,
                extraInner = mapOf("security" to securityJson),
            ).requireOk("get_photo")
            return imageFromPhotoData(withTimeout(timeoutMs) { photo.await() })
        } finally {
            mutex.withLock { pendingBlobs.remove(requestId) }
        }
    }

    private suspend fun sendRequest(
        requestId: Int,
        method: String,
        params: JsonElement,
        timeoutMs: Long,
        firstReplyTimeoutMs: Long? = null,
        extraInner: Map<String, JsonElement> = emptyMap(),
    ): V1Response {
        val ts = nowEpochSeconds()
        val request = PendingRequest()
        mutex.withLock { pending[requestId] = request }
        try {
            val v1Payload = V1Envelope.encodeRequestRaw(requestId, method, params, ts, extraInner)
            val frame = RoborockCodec(localKey, prefixed = false).encode(
                RoborockMessage(
                    protocol = RoborockProtocol.RPC_REQUEST,
                    payload = v1Payload,
                    seq = random.nextInt(SEQ_MIN, SEQ_MAX + 1),
                    random = random.nextInt(RANDOM_MIN, RANDOM_MAX + 1),
                    timestamp = ts,
                )
            )
            println("[VacLocal] publish method=$method id=$requestId topic=${topics.publishTopic} bytes=${frame.size}")
            mqtt.publish(topics.publishTopic, frame)
            if (firstReplyTimeoutMs != null) {
                withTimeoutOrNull(firstReplyTimeoutMs) { request.heard.await() } ?: throw RobotSilentException(method)
            }
            return withTimeout(timeoutMs) { request.answer.await() }
        } finally {
            mutex.withLock { pending.remove(requestId) }
        }
    }

    /**
     * Move to the current session's topics if they have changed. Cheap enough to call from the
     * poll loop, which is what keeps a silently renewed session from publishing into the void.
     */
    suspend fun ensureSubscribed() {
        if (demo || collectorJob == null) return
        val wanted = topics.subscribeTopic
        val current = subscribedTopic
        if (wanted == current) return
        println("[VacLocal] session topics changed — resubscribing to $wanted")
        if (current != null) runCatching { mqtt.unsubscribe(current) }
        mqtt.subscribe(wanted)
        subscribedTopic = wanted
    }

    suspend fun close() {
        if (demo) return
        collectorJob?.cancelAndJoin()
        collectorJob = null
        subscribedTopic?.let { runCatching { mqtt.unsubscribe(it) } }
        subscribedTopic = null
        mutex.withLock {
            for ((_, request) in pending) {
                request.answer.cancel()
                request.heard.cancel()
            }
            pending.clear()
            pendingMap?.cancel()
            pendingMap = null
            pendingBlobs.values.forEach { it.cancel() }
            pendingBlobs.clear()
            security = null
        }
    }


    suspend fun fetchMap(security: SecurityData, timeoutMs: Long = 30_000L): MapResponse =
        requestMap(security, "get_map_v1", JsonArray(emptyList()), savedMap = false, timeoutMs = timeoutMs)

    /**
     * A saved map other than the live one, or with [backup] its on-robot backup, as the official app
     * fetched them for its map list: `get_multi_map {"map_index": 0}`, `{"is_bak": 1, "map_index": 0}`
     * (captured 2026-10-28). The robot answers ["ok"] (sometimes ["retry"] first) and then sends the
     * map as a frame carrying the request id.
     */
    suspend fun fetchSavedMap(security: SecurityData, mapFlag: Int, backup: Boolean, timeoutMs: Long = 30_000L): MapResponse =
        requestMap(security, "get_multi_map", savedMapParams(mapFlag, backup), savedMap = true, timeoutMs = timeoutMs)

    private suspend fun requestMap(
        security: SecurityData,
        method: String,
        params: JsonElement,
        savedMap: Boolean,
        timeoutMs: Long,
    ): MapResponse {
        if (demo) return MapResponse(requestId = 0, data = ByteArray(0))
        check(collectorJob != null) { "Session must be started before fetchMap" }
        val deferred = CompletableDeferred<MapResponse>()
        val requestId = nextRequestId()
        mutex.withLock {
            this.security = security
            pendingMap = deferred
            pendingMapId = if (savedMap) requestId else null
            if (savedMap) savedMapRequestIds += requestId
        }
        try {
            val ts = nowEpochSeconds()
            val securityJson = buildJsonObject {
                put("endpoint", security.endpoint)
                put("nonce", security.nonceHex)
            }
            val v1Payload = V1Envelope.encodeRequestRaw(
                requestId = requestId,
                method = method,
                params = params,
                timestamp = ts,
                extraInner = mapOf("security" to securityJson),
            )
            val frame = RoborockCodec(localKey, prefixed = false).encode(
                RoborockMessage(
                    protocol = RoborockProtocol.RPC_REQUEST,
                    payload = v1Payload,
                    seq = random.nextInt(SEQ_MIN, SEQ_MAX + 1),
                    random = random.nextInt(RANDOM_MIN, RANDOM_MAX + 1),
                    timestamp = ts,
                )
            )
            mqtt.publish(topics.publishTopic, frame)
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            mutex.withLock {
                pendingMap = null
                pendingMapId = null
            }
        }
    }

    private suspend fun handleIncoming(bytes: ByteArray) {
        val msgs = try {
            RoborockCodec(localKey, prefixed = false).decode(bytes)
        } catch (e: RoborockProtocolException) {
            println("[VacLocal] decode failed")
            return
        }
        println("[VacLocal] decoded ${msgs.size} msg(s)")
        for (m in msgs) {
            runCatching {
                val resp = V1Envelope.decodeResponse(m.payload)
                resp.id?.let { id ->
                    // Commands sent with need_retry get an immediate ["retry"] and the real
                    // answer later under the same id, so only the real answer completes them.
                    if (resp.isProvisionalRetry()) {
                        mutex.withLock { pending[id] }?.heard?.complete(Unit)
                    } else {
                        val request = mutex.withLock { pending.remove(id) }
                        request?.heard?.complete(Unit)
                        request?.answer?.complete(resp)
                    }
                }
                // Responses can contain camera credentials or SDP, including unsolicited/late replies.
                println("[VacLocal] rpc response id=${resp.id} error=${resp.error != null}")
            }
            val dps = V1Envelope.decodeDpsPush(m.payload)
            if (dps != null) {
                val push = dps.filterKeys { it != V1_DPS_REQUEST && it != V1_DPS_RESPONSE }
                if (push.isNotEmpty()) {
                    _dpsUpdates.update { prev -> prev + push }
                }
            }

            decodePhotoFrame(m)?.let { blob ->
                println("[VacLocal] photo frame id=${blob.requestId} bytes=${blob.data.size}")
                mutex.withLock { pendingBlobs[blob.requestId] }?.complete(blob.data)
            }

            val (sd, pendingMapDef) = mutex.withLock { security to pendingMap }
            if (sd != null && pendingMapDef != null) {
                runCatching { decodeMapResponse(m, sd) }.getOrNull()?.let { mapResp ->
                    val wanted = mutex.withLock {
                        pendingMapId?.let { it == mapResp.requestId } ?: (mapResp.requestId !in savedMapRequestIds)
                    }
                    if (wanted && !pendingMapDef.isCompleted) pendingMapDef.complete(mapResp)
                }
            }
        }
    }

    /** A photo frame, or null for anything else; a broken one is logged and dropped, so its request times out. */
    private fun decodePhotoFrame(m: RoborockMessage): BlobResponse? =
        runCatching { decodeBlobResponse(m) }
            .onFailure { println("[VacLocal] photo frame decode failed") }
            .getOrNull()

    private suspend fun nextRequestId(): Int = mutex.withLock {
        val id = nextId
        nextId = if (nextId >= requestIds.last) requestIds.first else nextId + 1
        id
    }

    companion object {
        // Together these cover 10000..32767, the span the robot is known to accept.
        /** The session behind a robot's own screens. */
        val PRIMARY_REQUEST_IDS = 10000..25999
        /** The always-on session that watches a robot for alerts. */
        val MONITOR_REQUEST_IDS = 26000..29999
        /** Short-lived reachability checks from the device list. */
        val PROBE_REQUEST_IDS = 30000..32767

        private const val SEQ_MIN = 100000
        private const val SEQ_MAX = 999999
        private const val RANDOM_MIN = 10000
        private const val RANDOM_MAX = 99999
        private const val V1_DPS_REQUEST = 101
        private const val V1_DPS_RESPONSE = 102
    }
}

/**
 * The robot's "still working" reply to a command sent with `need_retry: 1`: result `["retry"]`
 * and no error. Seen in a capture of the official app for save_map, name_segment and
 * split_segment; the final result follows with the same id.
 */
internal fun V1Response.isProvisionalRetry(): Boolean {
    if (error != null) return false
    val r = result as? kotlinx.serialization.json.JsonArray ?: return false
    val only = r.singleOrNull() as? kotlinx.serialization.json.JsonPrimitive ?: return false
    return only.isString && only.content == "retry"
}
