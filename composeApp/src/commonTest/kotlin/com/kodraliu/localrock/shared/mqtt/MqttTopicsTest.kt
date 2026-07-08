package com.kodraliu.localrock.shared.mqtt

import kotlin.test.Test
import kotlin.test.assertEquals

class MqttTopicsTest {

    @Test
    fun topicsFor_layout() {
        val t = mqttTopicsFor(userU = "42", mqttUsername = "c9cc7cc8", duid = "ABCDEF1234")
        assertEquals("rr/m/i/42/c9cc7cc8/ABCDEF1234", t.publishTopic)
        assertEquals("rr/m/o/42/c9cc7cc8/ABCDEF1234", t.subscribeTopic)
    }
}
