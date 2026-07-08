package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

data class MqttMessage(val topic: String, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is MqttMessage && other.topic == topic && other.payload.contentEquals(payload)
    override fun hashCode(): Int = 31 * topic.hashCode() + payload.contentHashCode()
}

class MqttClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


class MqttClient(private val transport: MqttNativeTransport) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _messages = MutableSharedFlow<MqttMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<MqttMessage> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val mutex = Mutex()
    private var conn: MqttNativeConnection? = null
    private var superviseJob: Job? = null
    private var closed = false
    private var params: Params? = null

    private var currentDisconnect: CompletableDeferred<Unit>? = null

    private val subscriptions = mutableMapOf<String, Int>()

    private data class Params(
        val creds: MqttCreds,
        val clientId: String,
        val keepAliveSeconds: Int,
        val connectTimeoutMs: Long,
    )


    suspend fun connect(
        creds: MqttCreds,
        clientId: String,
        connectTimeoutMs: Long = 15_000L,
        keepAliveSeconds: Int = 30,
    ) {
        mutex.withLock {
            check(superviseJob == null) { "Already connected" }
            closed = false
            params = Params(creds, clientId, keepAliveSeconds, connectTimeoutMs)
        }
        val firstConnect = CompletableDeferred<Unit>()
        val job = scope.launch { superviseLoop(firstConnect) }
        mutex.withLock { superviseJob = job }
        try {
            firstConnect.await()
        } catch (e: Throwable) {
            job.cancel()
            mutex.withLock {
                superviseJob = null
                conn = null
            }
            throw e
        }
    }


    suspend fun subscribe(topic: String, qos: Int = QOS_AT_LEAST_ONCE) {
        val c = mutex.withLock {
            subscriptions[topic] = qos
            conn
        }
        if (c != null && c.isConnected) {
            withContext(Dispatchers.Default) {
                runCatching { c.subscribe(topic, qos) }
                    .onFailure { println("[VacLocal] MQTT subscribe $topic failed (will retry on reconnect): ${it.message}") }
            }
        }
    }

    suspend fun unsubscribe(topic: String) {
        val c = mutex.withLock {
            subscriptions.remove(topic)
            conn
        }
        if (c != null && c.isConnected) {
            withContext(Dispatchers.Default) { runCatching { c.unsubscribe(topic) } }
        }
    }

    suspend fun publish(topic: String, payload: ByteArray, qos: Int = QOS_AT_LEAST_ONCE, retain: Boolean = false) {
        val c = mutex.withLock { conn }
        if (c == null || !c.isConnected) throw MqttClientException("MQTT not connected (reconnecting)")
        withContext(Dispatchers.Default) {
            try {
                c.publish(topic, payload, qos, retain)
            } catch (e: Throwable) {
                throw MqttClientException("MQTT publish failed: ${e.message}", e)
            }
        }
    }


    suspend fun forceReconnect() {
        val (c, d) = mutex.withLock {
            if (!_connected.value) return
            conn to currentDisconnect
        }
        if (d == null) return
        println("[VacLocal] MQTT forceReconnect — tearing down session to reconnect")
        runCatching { withContext(Dispatchers.Default) { c?.close() } }
        if (!d.isCompleted) d.complete(Unit)
    }

    suspend fun disconnect() {
        val c = mutex.withLock {
            closed = true
            subscriptions.clear()
            val existing = conn
            conn = null
            currentDisconnect = null
            existing
        }
        mutex.withLock { superviseJob }?.cancel()
        mutex.withLock { superviseJob = null }
        runCatching { c?.close() }
        _connected.value = false
    }

    private suspend fun superviseLoop(firstConnect: CompletableDeferred<Unit>) {
        val p = mutex.withLock { params } ?: return
        var backoffMs = INITIAL_BACKOFF_MS
        var firstAttempt = true
        while (coroutineContext.isActive && !isClosed()) {
            val connectResult = CompletableDeferred<Unit>()
            val disconnected = CompletableDeferred<Unit>()
            var c: MqttNativeConnection? = null
            try {
                // transport.open may open the socket synchronously and throw, so keep it in the try.
                c = transport.open(
                    host = p.creds.host,
                    port = p.creds.port,
                    tls = p.creds.tls,
                    username = p.creds.username,
                    password = p.creds.password,
                    clientId = p.clientId,
                    keepAliveSeconds = p.keepAliveSeconds,
                    onConnected = { if (!connectResult.isCompleted) connectResult.complete(Unit) },
                    onFailed = { reason ->
                        if (!connectResult.isCompleted) {
                            connectResult.completeExceptionally(MqttClientException("MQTT connect failed: $reason"))
                        }
                        if (!disconnected.isCompleted) disconnected.complete(Unit)
                    },
                    onMessage = { topic, payload -> _messages.tryEmit(MqttMessage(topic, payload)) },
                    onDisconnected = {
                        if (!connectResult.isCompleted) {
                            connectResult.completeExceptionally(MqttClientException("MQTT dropped before connect"))
                        }
                        if (!disconnected.isCompleted) disconnected.complete(Unit)
                    },
                )
                mutex.withLock {
                    conn = c
                    currentDisconnect = disconnected
                }
                withTimeout(p.connectTimeoutMs) { connectResult.await() }

                _connected.value = true
                backoffMs = INITIAL_BACKOFF_MS
                println("[VacLocal] MQTT connected host=${p.creds.host}:${p.creds.port} user=${p.creds.username}")
                resubscribeAll(c)
                if (firstAttempt) {
                    firstConnect.complete(Unit)
                    firstAttempt = false
                }

                disconnected.await()
                _connected.value = false
                println("[VacLocal] MQTT disconnected — will reconnect")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _connected.value = false
                println("[VacLocal] MQTT connect attempt failed: ${e::class.simpleName}: ${e.message}")
                if (firstAttempt) {
                    firstConnect.completeExceptionally(
                        MqttClientException("MQTT connect failed (host=${p.creds.host}:${p.creds.port}): ${e.message}", e)
                    )
                    return // finally still runs cleanup
                }
            } finally {
                val closing = c
                mutex.withLock {
                    if (conn === closing) conn = null
                    if (currentDisconnect === disconnected) currentDisconnect = null
                }
                closing?.let { runCatching { it.close() } }
            }

            if (isClosed()) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private suspend fun resubscribeAll(c: MqttNativeConnection) {
        val subs = mutex.withLock { subscriptions.toMap() }
        if (subs.isEmpty()) return
        for ((topic, qos) in subs) {
            runCatching { c.subscribe(topic, qos) }
                .onFailure { println("[VacLocal] MQTT resubscribe $topic failed: ${it.message}") }
        }
        println("[VacLocal] MQTT (re)subscribed ${subs.size} topic(s)")
    }

    private suspend fun isClosed(): Boolean = mutex.withLock { closed }

    private companion object {
        const val QOS_AT_LEAST_ONCE = 1
        const val INITIAL_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
