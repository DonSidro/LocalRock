package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Expected values follow python-roborock (device_features.py, v1_clean_modes.py). */
class SmartPlanTest {

    @Test
    fun feature_bit_reads_nibble_from_the_end() {
        // Bit 55 lives in the 14th char from the end, bit 3 of that nibble.
        assertTrue(newFeatureBit("80000000000000", 55))
        assertFalse(newFeatureBit("70000000000000", 55))
        assertTrue(newFeatureBit("1", 0))
        assertTrue(newFeatureBit("20", 5))
        assertFalse(newFeatureBit("20", 4))
    }

    @Test
    fun feature_bit_is_false_for_short_missing_or_invalid_strings() {
        assertFalse(newFeatureBit("8000000000000", 55))
        assertFalse(newFeatureBit(null, 55))
        assertFalse(newFeatureBit("", 55))
        assertFalse(newFeatureBit("z0000000000000", 55))
    }

    @Test
    fun smart_plan_support_is_bit_55() {
        assertTrue(isSmartPlanSupported("ff80000000000000"))
        assertFalse(isSmartPlanSupported("ff70000000000000"))
    }

    @Test
    fun smart_mode_is_set_when_any_value_is_smart() {
        assertTrue(isSmartModeSet(VacuumFanPower.SMART, WaterBoxMode.LOW, MopRoute.STANDARD))
        assertTrue(isSmartModeSet(VacuumFanPower.BALANCED, WaterBoxMode.SMART, MopRoute.STANDARD))
        assertTrue(isSmartModeSet(VacuumFanPower.BALANCED, WaterBoxMode.LOW, MopRoute.SMART))
        assertFalse(isSmartModeSet(VacuumFanPower.BALANCED, WaterBoxMode.LOW, MopRoute.STANDARD))
        assertFalse(isSmartModeSet(null, null, null))
    }

    @Test
    fun pure_water_flow_codes_read_back_as_the_level_that_was_set() {
        // Saros 10R (a232) stores 201/202/203 as 225/235/245 (observed via get_status).
        assertEquals(WaterBoxMode.LOW, WaterBoxMode.normalize(225))
        assertEquals(WaterBoxMode.MEDIUM, WaterBoxMode.normalize(235))
        assertEquals(WaterBoxMode.HIGH, WaterBoxMode.normalize(245))
        assertEquals(WaterBoxMode.OFF, WaterBoxMode.normalize(200))
        assertEquals(WaterBoxMode.SMART, WaterBoxMode.normalize(209))
        assertEquals(null, WaterBoxMode.normalize(null))
    }

    @Test
    fun clean_motor_mode_params_match_python_roborock() {
        assertEquals(
            """[{"fan_power":110,"water_box_mode":209,"mop_mode":306}]""",
            encodeCleanMotorModeParams(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART).toString(),
        )
    }
}
