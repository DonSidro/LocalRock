package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimerSettingsTest {

    @Test
    fun get_timer_answer_with_options_parses_them() {
        val answer = Json.parseToJsonElement(
            """
            [
              ["3549431", "on", ["00 11 * * 1,3,5",
                ["start_clean", {"repeat": 1, "water_box_mode": 203, "segments": "0", "fan_power": 104, "mop_mode": 300}]]],
              ["3549434", "off", ["00 11 * * 0,6", ["start_clean", {"water_box_mode": 200, "fan_power": 102}]]]
            ]
            """.trimIndent(),
        )

        val (monWedFri, weekend) = parseTimerList(answer)

        assertEquals(setOf(1, 3, 5), monWedFri.weekdays)
        assertEquals(TimerSettings(fanPower = 104, waterBoxMode = 203, mopMode = 300, repeat = 1), monWedFri.settings)
        assertEquals(TimerKind.VAC_AND_MOP, monWedFri.settings.kind)
        assertEquals(false, weekend.enabled)
        assertEquals(TimerKind.VACUUM, weekend.settings.kind)
    }

    @Test
    fun answers_without_options_still_parse() {
        // What a robot, or an older server, sends: an empty action object, or none at all.
        val answer = Json.parseToJsonElement(
            """[["1","on",["30 7 * * *",["start_clean",{}]]],["2","on",["0 9 * * 1",["start_clean"]]]]""",
        )

        val timers = parseTimerList(answer)

        assertEquals(2, timers.size)
        assertEquals(TimerSettings(), timers[0].settings)
        assertEquals(TimerSettings(), timers[1].settings)
        assertEquals(emptySet(), timers[0].weekdays)
    }

    @Test
    fun malformed_entries_are_skipped_and_a_non_list_is_empty() {
        val answer = Json.parseToJsonElement("""[["only-an-id"], "junk", ["3","on",["bad cron",["start_clean",{}]]]]""")

        assertEquals(emptyList(), parseTimerList(answer))
        assertEquals(emptyList(), parseTimerList(Json.parseToJsonElement("""{"status":1}""")))
        assertEquals(emptyList(), parseTimerList(null))
    }

    @Test
    fun no_options_send_the_empty_object_this_app_always_sent() {
        assertEquals("{}", TimerSettings().toActionObject().toString())
    }

    @Test
    fun chosen_options_are_sent_by_the_servers_key_names() {
        val settings = TimerSettings(fanPower = 102, waterBoxMode = 200, repeat = 2)

        assertEquals("""{"fan_power":102,"water_box_mode":200,"repeat":2}""", settings.toActionObject().toString())
    }

    @Test
    fun an_option_that_is_not_chosen_is_left_out_not_sent_as_null() {
        val after = TimerSettings(fanPower = 104)

        assertEquals("""{"fan_power":104}""", after.toActionObject().toString())
    }

    @Test
    fun cleaning_kinds_build_the_settings_the_official_app_uses() {
        assertEquals(
            TimerSettings(fanPower = 102, waterBoxMode = WaterBoxMode.OFF, repeat = 2),
            timerSettingsFor(TimerKind.VACUUM, fanPower = 102, waterBoxMode = 203, mopMode = 301, repeat = 2),
        )
        assertEquals(
            TimerSettings(fanPower = 104, waterBoxMode = 203, mopMode = 301),
            timerSettingsFor(TimerKind.VAC_AND_MOP, fanPower = 104, waterBoxMode = 203, mopMode = 301, repeat = null),
        )
        // No suction for Mop, and a water level is filled in because mopping without water would not mop.
        assertEquals(
            TimerSettings(fanPower = VacuumFanPower.OFF, waterBoxMode = WaterBoxMode.MEDIUM),
            timerSettingsFor(TimerKind.MOP, fanPower = 104, waterBoxMode = null, mopMode = null, repeat = null),
        )
        assertEquals(TimerSettings(repeat = 3), timerSettingsFor(TimerKind.CURRENT, 104, 203, 301, 3))
    }

    @Test
    fun kind_is_read_back_from_the_stored_codes() {
        assertEquals(TimerKind.CURRENT, TimerSettings().kind)
        assertEquals(TimerKind.CURRENT, TimerSettings(fanPower = 103).kind)
        assertEquals(TimerKind.VACUUM, TimerSettings(waterBoxMode = 200).kind)
        assertEquals(TimerKind.MOP, TimerSettings(fanPower = 105, waterBoxMode = 202).kind)
        assertEquals(TimerKind.VAC_AND_MOP, TimerSettings(fanPower = 104, waterBoxMode = 203).kind)
    }

    @Test
    fun summaries_read_like_the_choice() {
        assertNull(TimerSettings().summary())
        assertEquals("Vacuum · Balanced suction", TimerSettings(fanPower = 102, waterBoxMode = 200).summary())
        assertEquals(
            "Vacuum and mop · Max suction · High water · Deep route · 2 passes",
            TimerSettings(fanPower = 104, waterBoxMode = 203, mopMode = 301, repeat = 2).summary(),
        )
        assertEquals("Mop · Medium water", TimerSettings(fanPower = 105, waterBoxMode = 202).summary())
        // A code this app has no name for still shows up rather than vanishing.
        assertEquals("Vacuum and mop · Suction 999 · Water 777", TimerSettings(999, 777).summary())
    }

    @Test
    fun cron_round_trips_through_the_timer_list() {
        val cron = buildCron(minute = 30, hour = 7, weekdays = setOf(5, 1, 3))
        assertEquals("30 7 * * 1,3,5", cron)

        val timer = parseCronTimer("9", enabled = true, cron = cron)

        assertEquals(CleanTimer("9", true, 7, 30, setOf(1, 3, 5)), timer)
    }
}
