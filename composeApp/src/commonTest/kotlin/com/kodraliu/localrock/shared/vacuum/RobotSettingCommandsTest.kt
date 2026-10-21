package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RobotSettingCommandsTest {

    @Test
    fun volume_and_child_lock_use_the_command_names_robots_answer_to() {
        // The official app sets the volume with change_sound_volume (captured: [30], then [40], [45]).
        // A Qrevo Pro answers "The method called is not recognized by the device" to set_sound_volume,
        // get_child_lock and set_child_lock, which is why changes did nothing (#32).
        assertEquals("get_sound_volume", GET_SOUND_VOLUME)
        assertEquals("change_sound_volume", CHANGE_SOUND_VOLUME)
        assertEquals("get_child_lock_status", GET_CHILD_LOCK_STATUS)
        assertEquals("set_child_lock_status", SET_CHILD_LOCK_STATUS)
    }

    @Test
    fun the_child_lock_is_sent_as_a_bare_object() {
        // `[{"lock_status": 1}]` was refused with -10005 "Invalid parameters" (checked on a Qrevo Pro).
        assertEquals("""{"lock_status":1}""", childLockParams(true).toString())
        assertEquals("""{"lock_status":0}""", childLockParams(false).toString())
    }

    @Test
    fun a_refused_change_is_an_error_not_a_success() {
        val refused = V1Response(
            id = 1,
            result = null,
            error = Json.parseToJsonElement("""{"code":-10005,"message":"Param error"}""") as JsonObject,
        )

        assertFailsWith<RobotCommandException> { refused.requireOk(CHANGE_SOUND_VOLUME) }
    }
}
