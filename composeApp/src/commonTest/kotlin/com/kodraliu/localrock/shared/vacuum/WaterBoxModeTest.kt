package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class WaterBoxModeTest {
    @Test
    fun slide_codes_round_trip_through_normalize() {
        for (level in listOf(WaterBoxMode.LOW, WaterBoxMode.MEDIUM, WaterBoxMode.HIGH)) {
            assertEquals(level, WaterBoxMode.normalize(WaterBoxMode.forSlide(level)))
        }
        assertEquals(225, WaterBoxMode.forSlide(WaterBoxMode.LOW))
        assertEquals(235, WaterBoxMode.forSlide(WaterBoxMode.MEDIUM))
        assertEquals(245, WaterBoxMode.forSlide(WaterBoxMode.HIGH))
    }

    @Test
    fun levels_without_a_slide_code_pass_through() {
        assertEquals(WaterBoxMode.OFF, WaterBoxMode.forSlide(WaterBoxMode.OFF))
        assertEquals(WaterBoxMode.SMART, WaterBoxMode.forSlide(WaterBoxMode.SMART))
    }
}
