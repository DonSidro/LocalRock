package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.schedule.ServerSchedule
import kotlinx.serialization.json.*
import kotlin.test.*

class ServerJobEditingTest {
    private fun job(cron: String = "10 9 ? * 0,6", repeated: Boolean = true) = ServerSchedule(buildJsonObject {
        put("id", "http-id"); put("enabled", false); put("cron", cron); put("repeated", repeated)
        put("param", buildJsonObject {
            put("id", 1); put("method", "server_scheduled_start"); put("futureParam", true)
            put("params", JsonArray(listOf(buildJsonObject {
                put("name", "native-name"); put("segments", "20,17"); put("map_index", 1)
                put("fan_power", 102); put("water_box_mode", 202); put("mop_mode", 300)
                put("clean_mop", 1); put("futureOption", "keep")
            })))
        })
    })

    @Test fun time_only_edit_preserves_all_settings_and_enablement() {
        val timer = job().toCleanTimer()
        val patch = serverJobEditPatch(timer, 10, 20, setOf(1,2), timer.settings, false, null, false, true)
        assertEquals(setOf("cron", "repeated"), patch.keys)
        assertEquals("20 10 ? * 1,2", patch["cron"]?.jsonPrimitive?.content)
        assertFalse("enabled" in patch)
    }
    @Test fun settings_edit_preserves_native_reference_rooms_and_unknown_fields() {
        val original = job(); val timer = original.toCleanTimer()
        val patch = serverJobEditPatch(timer, 9, 10, setOf(0,6), timer.settings.copy(fanPower = 103), false, null, true, false)
        val saved = ServerSchedule(JsonObject(original.raw + patch))
        assertEquals(original.settings!! - "fan_power", saved.settings!! - "fan_power")
        assertEquals(original.param!!["futureParam"], saved.param!!["futureParam"])
        assertFalse("cron" in patch); assertFalse("enabled" in patch)
    }
    @Test fun date_specific_and_repeated_false_jobs_remain_once() {
        val dated = job("15 8 31 10 ?", false).toCleanTimer()
        assertEquals(TimerDate(10,31), dated.date); assertTrue(dated.editable)
        val once = job(repeated = false).toCleanTimer()
        assertFalse(once.serverJob!!.repeated)
        assertTrue(serverJobEditPatch(once, once.hour, once.minute, once.weekdays, once.settings, true, TimerDate(10,27), false, false).isEmpty())
    }
    @Test fun unsupported_cron_is_visible_but_not_rewritten() {
        val timer = job("*/15 * * * *").toCleanTimer()
        assertFalse(timer.editable); assertEquals("http-id", timer.id)
        assertFailsWith<RuntimeException> { serverJobEditPatch(timer, 9,10, emptySet(), timer.settings, false,null,false,true) }
    }
    @Test fun weekly_ranges_are_not_silently_lost() {
        assertEquals(setOf(1,2,3,4,5), job("10 9 ? * 1-5").toCleanTimer().weekdays)
    }
}
