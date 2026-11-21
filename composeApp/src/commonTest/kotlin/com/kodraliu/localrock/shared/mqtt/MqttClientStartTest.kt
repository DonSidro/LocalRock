package com.kodraliu.localrock.shared.mqtt

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
