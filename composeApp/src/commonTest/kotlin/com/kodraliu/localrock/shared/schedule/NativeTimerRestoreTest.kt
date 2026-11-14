package com.kodraliu.localrock.shared.schedule

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NativeTimerRestoreTest {
    private fun job(cron: String = "00 11 ? * 1,3,5", segments: String = "0", name: String? = "1759965417864") = ServerSchedule(
        Json.parseToJsonElement(
            """{"id":3549431,"cron":"$cron","timeZoneId":"America/New_York","repeated":true,"enabled":true,
            "param":{"id":1,"method":"server_scheduled_start","params":[{"repeat":1,"water_box_mode":203,"segments":"$segments",
            "fan_power":104,"mop_mode":300,"clean_mop":1,"map_index":-1${name?.let { ",\"name\":\"$it\"" } ?: ""}}]}}""",
        ).jsonObject,
    )

    @Test fun weeklyCronSwapsQuestionMarkForStar() { assertEquals("00 11 * * 1,3,5", robotCronOf("00 11 ? * 1,3,5")) }

    @Test fun onceCronSwapsTheTrailingField() { assertEquals("30 9 14 11 *", robotCronOf("30 9 14 11 ?")) }

    @Test fun malformedCronIsRejected() { assertNull(robotCronOf("00 11 *")) }

    @Test fun wholeHomeJobCanBeRestored() { assertNull(restoreBlockedReason(job())) }

    @Test fun roomJobIsBlocked() { assertNotNull(restoreBlockedReason(job(segments = "16,17"))) }

    @Test fun jobWithoutNameIsBlocked() { assertNotNull(restoreBlockedReason(job(name = null))) }
}
