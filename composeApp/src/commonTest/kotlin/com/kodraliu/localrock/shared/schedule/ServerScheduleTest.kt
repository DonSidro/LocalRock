package com.kodraliu.localrock.shared.schedule

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerScheduleTest {
    private fun imported(stringParam: Boolean = false): ServerSchedule {
        val param = Json.parseToJsonElement("""{"id":1,"method":"server_scheduled_start","params":[{"name":"native-123","map_index":1,"segments":"20,17","repeat":1,"clean_order_mode":2,"mop_template_id":7,"future":{"x":true}}],"futureParam":"keep"}""")
        return ServerSchedule(buildJsonObject {
            put("id", 42); put("cron", "28 8 ? * 1,2,3,4,5"); put("timeZoneId", "America/Los_Angeles")
            put("enabled", true); put("repeated", true); put("param", if (stringParam) JsonPrimitive(param.toString()) else param)
        })
    }
    @Test fun time_only_update_omits_param() {
        val job = imported()
        val patch = job.update(WeeklyTime(9, 7, setOf(0, 6)), job.timezone, false)
        assertEquals("7 9 ? * 0,6", patch["cron"]!!.jsonPrimitive.content)
        assertFalse("param" in patch)
        assertFalse(patch["enabled"]!!.jsonPrimitive.boolean)
    }
    @Test fun settings_edit_keeps_native_identity_and_unknown_fields() {
        for (stringParam in listOf(false, true)) {
            val job = imported(stringParam)
            val patch = job.update(job.weekly!!, job.timezone, true, mapOf("segments" to JsonPrimitive("18")))
            val param = patch["param"]!!.jsonObject
            assertEquals(job.param!!["futureParam"], param["futureParam"])
            val entry = param["params"]!!.jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("18"), entry["segments"])
            assertEquals(job.settings!!.filterKeys { it != "segments" }, entry.filterKeys { it != "segments" })
        }
    }
    @Test fun complex_schedules_are_not_rewritten() {
        for (cron in listOf("*/5 8 * * *", "0 8 24 10 *", "0 8,9 * * 1", "0 24 * * 1", "0 8 * * 8", "0 8 * * 1,")) {
            assertNull(WeeklyTime.parse(cron), cron)
        }
        val once = ServerSchedule(JsonObject(imported().raw + ("repeated" to JsonPrimitive(false))))
        assertTrue(once.editable)
        val patch = once.update(WeeklyTime(8, 0, setOf(1)), "UTC", true)
        assertFalse(patch["repeated"]!!.jsonPrimitive.boolean)
        assertFalse("param" in patch)
    }
    @Test fun weekday_ranges_and_sunday_alias_are_supported() {
        assertEquals(setOf(1,2,3,4,5), WeeklyTime.parse("10 8 * * 1-5")!!.days)
        assertEquals(setOf(0,6), WeeklyTime.parse("10 8 ? * 6,7")!!.days)
        assertEquals((0..6).toSet(), WeeklyTime.parse("10 8 ? * *")!!.days)
        assertFailsWith<IllegalArgumentException> { WeeklyTime(8, 0, emptySet()).cron() }
    }
    @Test fun new_jobs_are_disabled_and_have_no_native_timer_identity() {
        val body = ServerSchedule.create(WeeklyTime(8, 0, setOf(1)), "UTC", mapOf("segments" to JsonPrimitive("18,19")))
        assertFalse(body["enabled"]!!.jsonPrimitive.boolean)
        assertNull(body["param"]!!.jsonObject["params"]!!.jsonArray[0].jsonObject["name"])
        assertFailsWith<IllegalArgumentException> { ServerSchedule.create(WeeklyTime(8,0,setOf(1)), "UTC", mapOf("name" to JsonPrimitive("bad"))) }
    }
    @Test fun native_timer_states_are_not_inferred_from_job_enabled() {
        assertEquals("On", nativeTimerState(Json.parseToJsonElement("""[["x","on",{}]]"""), "x"))
        assertEquals("Off", nativeTimerState(Json.parseToJsonElement("""[["x","off",{}]]"""), "x"))
        assertEquals("Missing", nativeTimerState(JsonArray(emptyList()), "x"))
        assertEquals("Unknown", nativeTimerState(null, "x"))
        assertEquals("Ambiguous", nativeTimerState(Json.parseToJsonElement("""[["x","on"],["x","off"]]"""), "x"))
    }
}
