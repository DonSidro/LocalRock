package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CarpetModeTest {
    @Test
    fun theLabelsFollowTheValuesTheRobotUses() {
        val options = carpetModeOptions(1)
        assertEquals("Avoid carpet", options[0])
        assertEquals("Rise (vacuum only)", options[1])
        assertEquals("Vacuum and mop (can wet carpet)", options[2])
    }

    @Test
    fun theOptionThatMopsTheCarpetSaysSo() {
        assertTrue("wet" in carpetModeOptions(1)[2])
        assertTrue("mop" in carpetModeOptions(1)[2])
    }

    @Test
    fun noBoostLabelRemains() {
        assertTrue(carpetModeOptions(1).none { it.contains("boost", ignoreCase = true) })
    }

    @Test
    fun dynamicLiftAppearsOnlyWhenTheRobotIsInIt() {
        assertEquals(3, carpetModeOptions(0).size)
        assertEquals(3, carpetModeOptions(2).size)
        assertEquals("Dynamic lift", carpetModeOptions(CARPET_MODE_DYNAMIC_LIFT)[3])
    }
}
