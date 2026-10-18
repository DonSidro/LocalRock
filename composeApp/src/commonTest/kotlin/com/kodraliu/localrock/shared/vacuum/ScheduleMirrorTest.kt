package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleMirrorTest {

    private val settings = TimerSettings(
        repeat = 1, waterBoxMode = 202, segments = "0", mopMode = 300, fanPower = 102, cleanMop = 1, mapIndex = -1,
    )

    @Test
    fun a_create_body_matches_what_the_official_app_posted_for_a_once_schedule() {
        // POST /user/devices/{duid}/jobs, captured live.
        val captured = Json.parseToJsonElement(
            """{"id":"","cron":"15 23 18 10 ?","repeated":false,"enabled":true,"param":{"id":1,
            "method":"server_scheduled_start","params":[{"repeat":1,"water_box_mode":202,"segments":"0",
            "fan_power":102,"mop_mode":300,"clean_mop":1,"map_index":-1,"name":"1792329301298"}]}}""",
        )

        val body = mirrorJobBody(
            cron = mirrorCron(minute = 15, hour = 23, weekdays = emptySet(), date = TimerDate(month = 10, day = 18)),
            enabled = true,
            repeated = false,
            settings = settings,
            robotId = "1792329301298",
            forCreate = true,
        )

        assertEquals(captured, body)
    }

    @Test
    fun an_update_body_matches_the_official_apps_put_and_has_no_id() {
        // PUT /user/devices/{duid}/jobs/{id}, captured live (a SmartPlan weekend schedule).
        val captured = Json.parseToJsonElement(
            """{"cron":"00 11 ? * 0,6","repeated":true,"enabled":true,"param":{"id":1,
            "method":"server_scheduled_start","params":[{"repeat":1,"water_box_mode":209,"segments":"0",
            "fan_power":110,"mop_mode":306,"clean_mop":1,"map_index":-1,"name":"1792291871087"}]}}""",
        )

        val body = mirrorJobBody(
            // The record's minute is zero-padded by the official app; the server reads either.
            cron = "00 11 ? * 0,6",
            enabled = true,
            repeated = true,
            settings = TimerSettings(
                repeat = 1, waterBoxMode = 209, segments = "0", mopMode = 306, fanPower = 110, cleanMop = 1, mapIndex = -1,
            ),
            robotId = "1792291871087",
            forCreate = false,
        )

        assertEquals(captured, body)
    }

    @Test
    fun the_record_cron_uses_question_marks_where_the_robots_uses_stars() {
        assertEquals("30 7 ? * 1,3,5", mirrorCron(30, 7, setOf(5, 1, 3), null))
        assertEquals("30 7 18 10 ?", mirrorCron(30, 7, emptySet(), TimerDate(10, 18)))
        // Every day lists all seven, on the robot and in the record.
        assertEquals("30 7 ? * 0,1,2,3,4,5,6", mirrorCron(30, 7, emptySet(), null))
        assertEquals("30 7 * * 0,1,2,3,4,5,6", buildCron(30, 7, emptySet()))
        assertEquals("30 7 18 10 *", buildCron(30, 7, emptySet(), TimerDate(10, 18)))
    }

    @Test
    fun an_every_day_cron_reads_back_as_every_day() {
        val timer = parseCronTimer("1", enabled = true, cron = "30 7 * * 0,1,2,3,4,5,6")

        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6), timer?.weekdays)
        assertEquals(null, timer?.date)
    }
}
