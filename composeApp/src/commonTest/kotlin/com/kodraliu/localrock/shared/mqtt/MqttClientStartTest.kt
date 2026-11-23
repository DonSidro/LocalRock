package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/** The supervisor's own clock is real time, so these wait on [MqttClient.connected], not on delays. */
class MqttClientStartTest {

    private class FakeConnection : MqttNativeConnection {
        override val isConnected: Boolean = true
        override fun subscribe(topic: String, qos: Int) {}
        override fun unsubscribe(topic: String) {}
        override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {}
        override fun close() {}
    }

    /** Fails the first [failures] opens the way an unreachable host does, then connects. */
    private class FlakyTransport(private val failures: Int) : MqttNativeTransport {
        var opens = 0
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            if (++opens <= failures) throw RuntimeException("Unable to resolve host")
            onConnected()
            return FakeConnection()
        }
    }

    /** Like [FlakyTransport], and also notes when each open happened. */
    private class TimedFlakyTransport(private val failures: Int) : MqttNativeTransport {
        val opens = mutableListOf<TimeSource.Monotonic.ValueTimeMark>()
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            opens += TimeSource.Monotonic.markNow()
            if (opens.size <= failures) throw RuntimeException("Connection refused")
            onConnected()
            return FakeConnection()
        }
    }

    /** Never answers the first [silent] opens, like a broker that accepts the socket and stalls. */
    private class StallingTransport(private val silent: Int) : MqttNativeTransport {
        var opens = 0
        override fun open(
            host: String,
            port: Int,
            tls: Boolean,
            username: String,
            password: String,
            clientId: String,
            keepAliveSeconds: Int,
            onConnected: () -> Unit,
            onFailed: (reason: String) -> Unit,
            onMessage: (topic: String, payload: ByteArray) -> Unit,
            onDisconnected: () -> Unit,
        ): MqttNativeConnection {
            if (++opens > silent) onConnected()
            return FakeConnection()
        }
    }

    private val creds = MqttCreds(host = "localhost", port = 8883, tls = true, username = "u", password = "p")

    @Test
    fun a_failed_first_attempt_is_retried_until_the_link_is_up() = runTest {
        val transport = FlakyTransport(failures = 1)
        val client = MqttClient(transport)
        try {
            // Returns although the first attempt fails; it used to throw and stop trying.
            client.start(creds, clientId = "test")
            client.connected.first { it }
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun starting_twice_keeps_the_one_connection() = runTest {
        val transport = FlakyTransport(failures = 0)
        val client = MqttClient(transport)
        try {
            client.start(creds, clientId = "test")
            client.start(creds, clientId = "test")
            client.connected.first { it }
            assertEquals(1, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun the_client_can_be_started_again_after_a_disconnect() = runTest {
        val transport = FlakyTransport(failures = 0)
        val client = MqttClient(transport)
        try {
            client.start(creds, clientId = "first")
            client.connected.first { it }
            client.disconnect()
            assertFalse(client.connected.value)

            client.start(creds, clientId = "second")
            client.connected.first { it }
            assertTrue(client.connected.value)
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun a_connect_that_times_out_is_retried() = runTest {
        val transport = StallingTransport(silent = 1)
        val client = MqttClient(transport)
        try {
            // A timed-out attempt used to end the supervisor, leaving the app offline until restarted.
            client.start(creds, clientId = "test", connectTimeoutMs = 200)
            client.connected.first { it }
            assertEquals(2, transport.opens)
        } finally {
            client.disconnect()
        }
    }

    @Test
    fun a_fresh_login_does_not_restart_the_wait_from_the_shortest_step() = runTest {
        // Waits of 20, 40 and 80 ms. After the third failure the client signs in again; the wait
        // before the fourth attempt used to drop back to 20 ms, so an unreachable broker caused a
        // new login every few seconds (#131).
        val transport = TimedFlakyTransport(failures = 3)
        val client = MqttClient(transport, initialBackoffMs = 20)
        var refreshes = 0
        client.credentialRefresher = { _, _ -> refreshes++; creds.copy(password = "fresh") }
        try {
            client.start(creds, clientId = "test")
            client.connected.first { it }
            assertEquals(4, transport.opens.size)
            assertEquals(1, refreshes)
            val waitAfterLogin = transport.opens[3] - transport.opens[2]
            assertTrue(waitAfterLogin.inWholeMilliseconds >= 70, "waited only $waitAfterLogin after the re-login")
        } finally {
            client.disconnect()
        }
    }
}
