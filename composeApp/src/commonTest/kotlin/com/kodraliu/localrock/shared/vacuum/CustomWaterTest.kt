package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomWaterTest {
    @Test
    fun qrevo_pro_uses_the_spin_mop_range() {
        val offset = CustomWater.offsetFor("roborock.vacuum.a101")
        assertEquals(25, offset)
        // Measured on the robot: Extreme at the wet end reads distance_off 85.
        assertEquals(85, CustomWater.distanceFor(30, offset))
        assertEquals(30, CustomWater.levelOf(85, offset))
        assertEquals(WaterFlowBand.EXTREME, CustomWater.band(85, offset))
        // The official app labels 26 Moderate.
        assertEquals(WaterFlowBand.MODERATE, CustomWater.band(CustomWater.distanceFor(26, offset), offset))
        assertEquals(230, CustomWater.distanceFor(1, offset))
        assertEquals(WaterFlowBand.SLIGHT, CustomWater.band(230, offset))
    }

    @Test
    fun other_models_use_the_base_range() {
        assertEquals(0, CustomWater.offsetFor("roborock.vacuum.a15"))
        assertEquals(60, CustomWater.distanceFor(30, 0))
        assertEquals(205, CustomWater.distanceFor(1, 0))
        assertEquals(WaterFlowBand.HIGH, CustomWater.band(75, 0))
        assertEquals(WaterFlowBand.MEDIUM, CustomWater.band(130, 0))
        assertNull(CustomWater.levelOf(300, 0))
    }

    @Test
    fun support_is_robot_feature_bit_31() {
        assertTrue(CustomWater.isSupported((1L shl 31).toString()))
        assertFalse(CustomWater.isSupported("1"))
        assertFalse(CustomWater.isSupported(null))
    }
}
