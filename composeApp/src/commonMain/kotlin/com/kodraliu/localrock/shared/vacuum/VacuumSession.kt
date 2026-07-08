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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

class VacuumException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


class VacuumSession(
    private val localKey: String,
    private val mqtt: MqttClient,
    private val topics: MqttTopics,
    private val nowEpochSeconds: () -> Int,
    private val random: Random = Random.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val demo: Boolean = false,
) {
    // In demo mode the session performs no MQTT I/O: start/close are no-ops and every command
    // resolves to an empty response, so the repository's control methods become harmless no-ops.
    private val emptyResponse = V1Response(id = null, result = null, error = null)
    private val mutex = Mutex()
    private var nextId = random.nextInt(REQUEST_ID_MIN, REQUEST_ID_MAX + 1)
    private val pending = mutableMapOf<Int, CompletableDeferred<V1Response>>()
    private val _dpsUpdates = MutableStateFlow<Map<Int, JsonElement>>(emptyMap())
    val dpsUpdates: StateFlow<Map<Int, JsonElement>> = _dpsUpdates
    private var collectorJob: Job? = null
    private var security: SecurityData? = null
    private var pendingMap: CompletableDeferred<MapResponse>? = null

    suspend fun start() {
        if (demo) return
        check(collectorJob == null) { "Session already started" }
        mqtt.subscribe(topics.subscribeTopic)
        collectorJob = scope.launch {
            mqtt.messages.collect { msg ->
                if (msg.topic == topics.subscribeTopic) {
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

    suspend fun sendCommandRaw(
        method: String,
        params: JsonElement,
        timeoutMs: Long = 10_000L,
    ): V1Response {
        if (demo) return emptyResponse
        val requestId = nextRequestId()
        val ts = nowEpochSeconds()
        val deferred = CompletableDeferred<V1Response>()
        mutex.withLock { pending[requestId] = deferred }
        try {
            val v1Payload = V1Envelope.encodeRequestRaw(requestId, method, params, ts)
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
            return withTimeout(timeoutMs) { deferred.await() }
        } finally {
            mutex.withLock { pending.remove(requestId) }
        }
    }

    suspend fun close() {
        if (demo) return
        collectorJob?.cancelAndJoin()
        collectorJob = null
        runCatching { mqtt.unsubscribe(topics.subscribeTopic) }
        mutex.withLock {
            for ((_, def) in pending) def.cancel()
            pending.clear()
            pendingMap?.cancel()
            pendingMap = null
            security = null
        }
    }


    suspend fun fetchMap(security: SecurityData, timeoutMs: Long = 30_000L): MapResponse {
        if (demo) return MapResponse(requestId = 0, data = ByteArray(0))
        check(collectorJob != null) { "Session must be started before fetchMap" }
        val deferred = CompletableDeferred<MapResponse>()
        mutex.withLock {
            this.security = security
            pendingMap = deferred
        }
        try {
            val ts = nowEpochSeconds()
            val securityJson = buildJsonObject {
                put("endpoint", security.endpoint)
                put("nonce", security.nonceHex)
            }
            val v1Payload = V1Envelope.encodeRequest(
                requestId = nextRequestId(),
                method = "get_map_v1",
                params = emptyList(),
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
            mutex.withLock { pendingMap = null }
        }
    }

    private suspend fun handleIncoming(bytes: ByteArray) {
        val msgs = try {
            RoborockCodec(localKey, prefixed = false).decode(bytes)
        } catch (e: RoborockProtocolException) {
            println("[VacLocal] decode FAILED (localKey mismatch?): ${e.message}")
            return
        }
        println("[VacLocal] decoded ${msgs.size} msg(s)")
        for (m in msgs) {
            runCatching {
                val resp = V1Envelope.decodeResponse(m.payload)
                resp.id?.let { id ->
                    val def = mutex.withLock { pending.remove(id) }
                    def?.complete(resp)
                }
                println("[VacLocal] rpc response id=${resp.id} result=${resp.result} error=${resp.error}")
            }
            val dps = V1Envelope.decodeDpsPush(m.payload)
            if (dps != null) {
                val push = dps.filterKeys { it != V1_DPS_REQUEST && it != V1_DPS_RESPONSE }
                if (push.isNotEmpty()) {
                    _dpsUpdates.update { prev -> prev + push }
                }
            }

            val (sd, pendingMapDef) = mutex.withLock { security to pendingMap }
            if (sd != null && pendingMapDef != null) {
                runCatching { decodeMapResponse(m, sd) }.getOrNull()?.let { mapResp ->
                    if (!pendingMapDef.isCompleted) pendingMapDef.complete(mapResp)
                }
            }
        }
    }

    private suspend fun nextRequestId(): Int = mutex.withLock {
        val id = nextId
        nextId = if (nextId >= REQUEST_ID_MAX) REQUEST_ID_MIN else nextId + 1
        id
    }

    private companion object {
        const val REQUEST_ID_MIN = 10000
        const val REQUEST_ID_MAX = 32767
        const val SEQ_MIN = 100000
        const val SEQ_MAX = 999999
        const val RANDOM_MIN = 10000
        const val RANDOM_MAX = 99999
        const val V1_DPS_REQUEST = 101
        const val V1_DPS_RESPONSE = 102
    }
}
