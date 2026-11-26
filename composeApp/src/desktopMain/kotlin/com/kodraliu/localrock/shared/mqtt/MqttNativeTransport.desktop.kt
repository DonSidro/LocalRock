package com.kodraliu.localrock.shared.mqtt

import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Desktop MQTT transport backed by Eclipse Paho (TLS via the JDK trust store).
 *
 * Paho names the server in the TLS handshake (SNI) and checks that the certificate belongs to
 * that name. KMQTT 1.0.0, used before, did neither: a reverse proxy that picks its certificate by
 * server name had nothing to go on and dropped the connection (issue #18).
 */
actual fun defaultMqttTransport(): MqttNativeTransport? = PahoTransport()

internal class PahoTransport : MqttNativeTransport {

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
        val dropped = AtomicBoolean(false)
        val scheme = if (tls) "ssl" else "tcp"
        val client = MqttAsyncClient("$scheme://$host:$port", clientId, MemoryPersistence())
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                if (dropped.compareAndSet(false, true)) onDisconnected()
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                onMessage(topic, message.payload ?: ByteArray(0))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        val options = MqttConnectOptions().apply {
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            isCleanSession = true
            // MqttClient owns reconnecting, with fresh credentials when the old ones expire.
            isAutomaticReconnect = false
            keepAliveInterval = keepAliveSeconds
            connectionTimeout = CONNECT_TIMEOUT_SECONDS
            maxInflight = MAX_INFLIGHT
            userName = username
            setPassword(password.toCharArray())
        }
        client.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) = onConnected()

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                onFailed(describe(exception))
            }
        })
        return PahoConnection(client)
    }

    /** Paho wraps the real cause (a TLS or socket error) in an MqttException that only has a code. */
    private fun describe(e: Throwable?): String {
        val cause = e?.cause ?: e
        return cause?.message?.takeIf { it.isNotBlank() } ?: cause?.let { it::class.simpleName } ?: "unknown error"
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 20
        /** Paho refuses to publish beyond this many unacknowledged messages; its default is 10. */
        const val MAX_INFLIGHT = 100
    }
}

private class PahoConnection(private val client: MqttAsyncClient) : MqttNativeConnection {
    override val isConnected: Boolean get() = client.isConnected

    override fun subscribe(topic: String, qos: Int) {
        client.subscribe(topic, qos)
    }

    override fun unsubscribe(topic: String) {
        client.unsubscribe(topic)
    }

    override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {
        client.publish(topic, payload, qos, retain)
    }

    override fun close() {
        runCatching { client.disconnectForcibly(0, DISCONNECT_TIMEOUT_MS) }
        runCatching { client.close(true) }
    }

    private companion object {
        const val DISCONNECT_TIMEOUT_MS = 1_000L
    }
}
