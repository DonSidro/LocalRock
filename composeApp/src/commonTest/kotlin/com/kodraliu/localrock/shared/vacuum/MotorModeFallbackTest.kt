package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Values from a Saros 20 Neo (a296, firmware 02.29.56) in issue #34. */
class MotorModeFallbackTest {

    @Test
    fun a_param_refusal_resends_the_robots_own_raw_codes() {
        // set_custom_mode [103] was refused; {"fan_power":103,"water_box_mode":235,"mop_mode":300} was accepted.
        val modes = motorModesForFallback(-10005, fanPower = 102, waterBoxMode = 235, mopMode = 300)
        assertEquals(MotorModes(102, 235, 300), modes)
        assertEquals(MotorModes(103, 235, 300), modes?.copy(fanPower = 103))
    }

    @Test
    fun other_refusals_are_not_retried() {
        assertNull(motorModesForFallback(-10007, 102, 235, 300))
        assertNull(motorModesForFallback(null, 102, 235, 300))
    }

    @Test
    fun an_unknown_value_is_not_guessed() {
        assertNull(motorModesForFallback(-10005, null, 235, 300))
        assertNull(motorModesForFallback(-10005, 102, null, 300))
        assertNull(motorModesForFallback(-10005, 102, 235, null))
    }

    @Test
    fun smart_plan_and_per_room_settings_are_left_alone() {
        assertNull(motorModesForFallback(-10005, VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART))
        assertNull(motorModesForFallback(-10005, VacuumFanPower.CUSTOM, WaterBoxMode.CUSTOM, MopRoute.CUSTOM))
    }

    @Test
    fun the_combined_params_carry_the_codes_unchanged() {
        assertEquals(
            """[{"fan_power":103,"water_box_mode":235,"mop_mode":300}]""",
            encodeCleanMotorModeParams(103, 235, 300).toString(),
        )
    }
}
