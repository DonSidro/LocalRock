package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RobotSettingParamsTest {
    @Test
    fun readsPickTheSettingByName() {
        assertEquals(Json.parseToJsonElement("""{"name":"full_duplex_switch"}"""), robotSettingReadParams("full_duplex_switch"))
    }

    @Test
    fun writesCarryTheNameAndAStatusNumber() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"enable_follow_low_obs_status","status":1}"""),
            robotSettingWriteParams("enable_follow_low_obs_status", true),
        )
        assertEquals(
            Json.parseToJsonElement("""{"name":"enable_follow_low_obs_status","status":0}"""),
            robotSettingWriteParams("enable_follow_low_obs_status", false),
        )
    }

    @Test
    fun theStatusReplyReadsAsAToggle() {
        assertEquals("status" to 1, parseBetaReply(Json.parseToJsonElement("""{"status":1}""")))
        assertEquals("status" to 0, parseBetaReply(Json.parseToJsonElement("""[{"status":0}]""")))
    }
}
