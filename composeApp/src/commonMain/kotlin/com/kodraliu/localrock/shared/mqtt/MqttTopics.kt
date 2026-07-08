package com.kodraliu.localrock.shared.mqtt


data class MqttTopics(
    val publishTopic: String,
    val subscribeTopic: String,
)

fun mqttTopicsFor(userU: String, mqttUsername: String, duid: String): MqttTopics = MqttTopics(
    publishTopic = "rr/m/i/$userU/$mqttUsername/$duid",
    subscribeTopic = "rr/m/o/$userU/$mqttUsername/$duid",
)
