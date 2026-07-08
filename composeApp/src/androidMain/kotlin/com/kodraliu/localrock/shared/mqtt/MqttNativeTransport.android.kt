@file:OptIn(ExperimentalUnsignedTypes::class)

package com.kodraliu.localrock.shared.mqtt

import io.github.davidepianca98.MQTTClient
import io.github.davidepianca98.mqtt.MQTTVersion
import io.github.davidepianca98.mqtt.Subscription
import io.github.davidepianca98.mqtt.packets.Qos
import io.github.davidepianca98.mqtt.packets.mqttv5.ReasonCode
import io.github.davidepianca98.mqtt.packets.mqttv5.SubscriptionOptions
import io.github.davidepianca98.socket.tls.TLSClientSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Android MQTT transport backed by KMQTT (JVM TLS via the platform trust store). */
actual fun defaultMqttTransport(): MqttNativeTransport? = KmqttTransport()

private class KmqttTransport : MqttNativeTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        val fireDisconnected = { if (dropped.compareAndSet(false, true)) onDisconnected() }

        val tlsSettings = if (tls) TLSClientSettings(serverCertificate = null) else null
        val client = MQTTClient(
            mqttVersion = MQTTVersion.MQTT3_1_1,
            address = host,
            port = port,
            tls = tlsSettings,
            keepAlive = keepAliveSeconds,
            userName = username,
            password = password.encodeToByteArray().toUByteArray(),
            clientId = clientId,
            onConnected = { onConnected() },
            onDisconnected = { fireDisconnected() },
            publishReceived = { publish ->
                onMessage(publish.topicName, publish.payload?.toByteArray() ?: ByteArray(0))
            },
        )
        client.runSuspend(Dispatchers.Default)


        val watchdog = scope.launch {
            while (isActive) {
                if (!client.isRunning()) {
                    fireDisconnected()
                    break
                }
                delay(2_000L)
            }
        }
        return KmqttConnection(client, watchdog)
    }
}

private class KmqttConnection(
    private val client: MQTTClient,
    private val watchdog: Job,
) : MqttNativeConnection {
    override val isConnected: Boolean get() = client.isConnackReceived()

    override fun subscribe(topic: String, qos: Int) {
        client.subscribe(listOf(Subscription(topic, SubscriptionOptions(qos.toQos()))))
    }

    override fun unsubscribe(topic: String) {
        client.unsubscribe(listOf(topic))
    }

    override fun publish(topic: String, payload: ByteArray, qos: Int, retain: Boolean) {
        client.publish(retain, qos.toQos(), topic, payload.toUByteArray())
    }

    override fun close() {
        watchdog.cancel()
        runCatching { client.disconnect(ReasonCode.SUCCESS) }
    }

    private fun Int.toQos(): Qos = when (this) {
        0 -> Qos.AT_MOST_ONCE
        2 -> Qos.EXACTLY_ONCE
        else -> Qos.AT_LEAST_ONCE
    }
}
