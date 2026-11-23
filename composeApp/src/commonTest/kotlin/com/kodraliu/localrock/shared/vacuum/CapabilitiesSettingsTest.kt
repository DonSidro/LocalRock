package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Feature sets read from the two test robots with app_get_init_status on 2026-11-13.
class CapabilitiesSettingsTest {
    private val curv = buildSettingsCapabilities("4499197267967999", "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF")
    private val pro = buildSettingsCapabilities("2247397454282751", "42BA8F5C7EDAFFFE")

    @Test
    fun both_robots_offer_charging_mode_and_child_lock() {
        for (c in listOf(curv, pro)) {
            assertTrue(c.chargingMode)
            assertTrue(c.childLock)
            assertTrue(c.offlineMap)
        }
    }

    @Test
    fun only_the_curv_has_a_voice_assistant() {
        assertTrue(curv.voiceAssistant)
        assertFalse(pro.voiceAssistant)
    }

    @Test
    fun adjusted_battery_level_follows_bit_72_which_neither_robot_sets() {
        // The Curv sets bit 74 but answers get_optimize_battery_status with "not recognized".
        assertTrue(newFeatureBit("00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF", 74))
        assertFalse(curv.adjustedBattery)
        assertFalse(pro.adjustedBattery)
        assertTrue(buildSettingsCapabilities(null, "1" + "0".repeat(18)).adjustedBattery)
    }

    @Test
    fun the_decimal_feature_set_is_read_bit_by_bit() {
        assertTrue(robotFeatureBit((1uL shl 45).toString(), 45))
        assertFalse(robotFeatureBit((1uL shl 45).toString(), 44))
        assertFalse(robotFeatureBit(null, 45))
        assertFalse(robotFeatureBit("not a number", 0))
    }

    @Test
    fun a_robot_with_no_feature_sets_shows_only_the_ungated_pages() {
        val none = buildSettingsCapabilities(null, null)
        assertTrue(none.hasCarpetSettings)
        assertTrue(none.hasDockSettings)
        assertFalse(none.chargingMode)
        assertFalse(none.voiceAssistant)
        assertFalse(none.hasFloorSettings)
    }
}
