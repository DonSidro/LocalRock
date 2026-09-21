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
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

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

    /**
     * Called when a reconnect attempt fails, with the number of consecutive failures so far.
     * Returning fresh credentials swaps them in for the next attempt; returning null keeps the
     * current ones. Lets a session that the server has expired recover without a manual re-login.
     */
    var credentialRefresher: (suspend (attempt: Int, error: Throwable) -> MqttCreds?)? = null

    @Volatile
    private var lastMessageMark: TimeMark? = null

    /**
     * Milliseconds since anything was last received, or [Long.MAX_VALUE] if nothing ever was.
     * A socket can stay "open" long after it stops delivering, so callers use this to tell a
     * genuinely dead link from one that is merely slow.
     */
    fun millisSinceLastMessage(): Long =
        lastMessageMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE

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
        var backoffMs = INITIAL_BACKOFF_MS
        var firstAttempt = true
        var failedAttempts = 0
        while (coroutineContext.isActive && !isClosed()) {
            // Re-read every iteration: credentials can be swapped out between attempts, and the
            // old code captured them once so a refreshed session could never take effect.
            val p = mutex.withLock { params } ?: return
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
                    onMessage = { topic, payload ->
                        lastMessageMark = TimeSource.Monotonic.markNow()
                        _messages.tryEmit(MqttMessage(topic, payload))
                    },
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
                failedAttempts = 0
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
                failedAttempts++
                println("[VacLocal] MQTT connect attempt failed (#$failedAttempts): ${e::class.simpleName}: ${e.message}")
                if (firstAttempt) {
                    firstConnect.completeExceptionally(
                        MqttClientException("MQTT connect failed (host=${p.creds.host}:${p.creds.port}): ${e.message}", e)
                    )
                    return // finally still runs cleanup
                }
                // The server mints a fresh session per login and rejects credentials from an
                // expired one, so repeated failures are more likely stale creds than a dead network.
                if (failedAttempts >= RELOGIN_AFTER_ATTEMPTS) {
                    val refreshed = runCatching { credentialRefresher?.invoke(failedAttempts, e) }
                        .onFailure { println("[VacLocal] MQTT credential refresh threw: ${it.message}") }
                        .getOrNull()
                    if (refreshed != null) {
                        println("[VacLocal] MQTT credentials refreshed — retrying as user=${refreshed.username}")
                        mutex.withLock { params = params?.copy(creds = refreshed) }
                        backoffMs = INITIAL_BACKOFF_MS
                        failedAttempts = 0
                    }
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
        /** Consecutive reconnect failures before asking for fresh credentials. */
        const val RELOGIN_AFTER_ATTEMPTS = 3
        const val MAX_BACKOFF_MS = 30_000L
    }
}
