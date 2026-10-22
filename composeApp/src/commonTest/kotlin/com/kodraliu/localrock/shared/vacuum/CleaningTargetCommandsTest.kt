package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals

class CleaningTargetCommandsTest {

    @Test
    fun zones_are_sent_as_the_official_app_sends_them() {
        // Captured from the official app on an S8 Pro Ultra; the robot went to state 17 and cleaned.
        assertEquals(
            "[[28050,26300,29050,27300,1]]",
            JsonArray(zonedCleanParams(listOf(ZoneRect(28050, 26300, 29050, 27300)))).toString(),
        )
    }

    @Test
    fun each_zone_is_its_own_entry() {
        assertEquals(
            "[[1000,2000,3000,4000,1],[5000,6000,7000,8000,2]]",
            JsonArray(zonedCleanParams(listOf(
                ZoneRect(1000, 2000, 3000, 4000),
                ZoneRect(5000, 6000, 7000, 8000, times = 2),
            ))).toString(),
        )
    }

    @Test
    fun the_goto_target_is_a_bare_point() {
        assertEquals("[26546,23100]", JsonArray(gotoTargetParams(26546, 23100)).toString())
    }
}
