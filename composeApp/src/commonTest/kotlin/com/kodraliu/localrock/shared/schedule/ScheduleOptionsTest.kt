package com.kodraliu.localrock.shared.schedule

import kotlinx.serialization.json.*
import kotlin.test.*

class ScheduleOptionsTest {
    @Test fun repeat_presets_encode_the_requested_days() {
        assertEquals(setOf(1, 2, 3, 4, 5), ScheduleRepeat.WEEKDAYS.days)
        assertEquals(setOf(0, 6), ScheduleRepeat.WEEKENDS.days)
        assertEquals((0..6).toSet(), ScheduleRepeat.EVERY_DAY.days)
        assertEquals("Mon, Wed, Sun", scheduleRepeatLabel(true, setOf(0, 1, 3)))
        assertEquals("Once", scheduleRepeatLabel(false, setOf(1, 3)))
    }

    @Test fun once_is_next_daily_occurrence_and_server_disables_it() {
        val body = ServerSchedule.create(WeeklyTime(8, 5, ScheduleRepeat.ONCE.days!!), "UTC", emptyMap(), repeated = false)
        assertEquals("5 8 ? * 0,1,2,3,4,5,6", body["cron"]!!.jsonPrimitive.content)
        assertFalse(body["repeated"]!!.jsonPrimitive.boolean)
        assertFalse(body["enabled"]!!.jsonPrimitive.boolean)
        val once = ServerSchedule(JsonObject(body + ("id" to JsonPrimitive(1))))
        assertTrue(once.editable)
        val daily = once.update(once.weekly!!, "UTC", true, repeated = true)
        assertTrue(daily["repeated"]!!.jsonPrimitive.boolean)
        assertFalse("param" in daily)
    }

    @Test fun cleaning_modes_use_explicit_motor_codes() {
        fun code(mode: ScheduleCleanMode, key: String) = mode.settings()[key]!!.jsonPrimitive.int
        assertEquals(200, code(ScheduleCleanMode.VACUUM, "water_box_mode"))
        assertEquals(105, code(ScheduleCleanMode.MOP, "fan_power"))
        assertEquals(102, code(ScheduleCleanMode.VAC_MOP, "fan_power"))
        assertEquals(202, code(ScheduleCleanMode.VAC_MOP, "water_box_mode"))
        assertEquals(106, code(ScheduleCleanMode.CUSTOM, "fan_power"))
        assertEquals(204, code(ScheduleCleanMode.CUSTOM, "water_box_mode"))
        assertEquals(302, code(ScheduleCleanMode.CUSTOM, "mop_mode"))
        ScheduleCleanMode.entries.forEach { mode ->
            assertEquals(mode, ScheduleCleanMode.from(JsonObject(mode.settings())))
        }
    }

    @Test fun unknown_motor_codes_are_not_guessed_or_overwritten() {
        assertNull(ScheduleCleanMode.from(null))
        assertNull(ScheduleCleanMode.from(buildJsonObject { put("fan_power", 999); put("water_box_mode", 200) }))
        assertEquals(ScheduleCleanMode.CUSTOM, ScheduleCleanMode.from(buildJsonObject { put("mop_mode", 302) }))
    }
}
