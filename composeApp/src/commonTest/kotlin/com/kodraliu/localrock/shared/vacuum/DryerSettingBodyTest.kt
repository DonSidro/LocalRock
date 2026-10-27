package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DryerSettingBodyTest {
    // The answer a Qrevo Pro, an S8 Pro Ultra and a Qrevo Curv 2 Flow were all seen to give.
    private val reported = Json.parseToJsonElement(
        """{"status":1,"on":{"cliff_on":1000,"cliff_off":1000,"count":10,"dry_time":14400,"dry_heating_film_time":3600},"off":{"cliff_on":500,"cliff_off":500,"count":10}}""",
    ).jsonObject

    @Test
    fun the_switch_changes_only_the_status() {
        val body = dryerSettingBody(reported, enabled = false, dryTimeSeconds = null)
        assertEquals(0, body["status"]!!.jsonPrimitive.int)
        assertEquals(reported["on"], body["on"])
        assertEquals(reported["off"], body["off"])
    }

    @Test
    fun the_drying_time_changes_only_dry_time_inside_the_on_block() {
        val body = dryerSettingBody(reported, enabled = true, dryTimeSeconds = 7200)
        val on = body["on"]!!.jsonObject
        assertEquals(7200, on["dry_time"]!!.jsonPrimitive.int)
        assertEquals(reported["on"]!!.jsonObject.filterKeys { it != "dry_time" }, on.filterKeys { it != "dry_time" })
        assertEquals(reported["off"], body["off"])
    }

    @Test
    fun without_an_answer_to_send_back_only_what_is_known_is_sent() {
        val body = dryerSettingBody(null, enabled = true, dryTimeSeconds = 7200)
        assertEquals(JsonObject(mapOf("status" to JsonPrimitive(1), "on" to JsonObject(mapOf("dry_time" to JsonPrimitive(7200))))), body)
    }
}
