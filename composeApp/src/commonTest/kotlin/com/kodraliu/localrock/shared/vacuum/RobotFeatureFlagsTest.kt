package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A robot paired through the local server has no `featureSet` / `newFeatureSet` on the server, so the
 * options gated on those flags come from its own app_get_init_status answer. The values are what a
 * Qrevo 2 Pro (a294, 02.44.86) answered on 2026-11-19.
 */
class RobotFeatureFlagsTest {
    private val qrevo2ProInitStatus = Json.parseToJsonElement(
        """[{"feature_info":[111,112,113,114,115,116,117,118,119,120,121,122,123,124,125],""" +
            """"new_feature_info":2247397454282751,""" +
            """"new_feature_info_str":"0000009021d0b410c0c57efda82934c952ba8f587edefffe"}]""",
    )
    private val flags = featureSetFromInitStatus(qrevo2ProInitStatus)
    private val info = newFeatureInfoFromInitStatus(qrevo2ProInitStatus)

    @Test
    fun init_status_gives_both_flag_fields() {
        assertEquals("0000009021d0b410c0c57efda82934c952ba8f587edefffe", flags)
        assertEquals(2247397454282751L, info)
    }

    @Test
    fun the_robots_flags_offer_the_options_it_advertises() {
        val ids = supportedBetaFeatures(flags).map { it.id }.toSet()
        for (id in listOf(
            "corner_mop_stretch", "carpet_deep_clean", "avoid_collision",
            "water_leak_check", "pet_supplies_deep_clean", "clean_fluid_delivery",
        )) {
            assertTrue(id in ids, "$id should be offered")
        }
        // Bits the a294 does not set stay hidden.
        for (id in listOf("right_brush_stretch", "gap_deep_clean", "dirty_object_detect", "voice_control_led")) {
            assertFalse(id in ids, "$id should not be offered")
        }
    }

    @Test
    fun without_any_flags_only_the_ungated_options_are_tried() {
        assertTrue(supportedBetaFeatures(null).all { it.bit == null })
        assertTrue(unlistedAdvertisedFeatures(null).isEmpty())
    }

    @Test
    fun dock_options_follow_the_robots_flags() {
        val fromRobot = DockCapabilities("roborock.vacuum.a294", info?.toString(), flags)
        assertEquals(listOf("Smart", "Light", "Balanced", "Max"), fromRobot.autoEmptyModes.map { it.second })
        assertEquals(WashFrequencyOption("Smart", 2), fromRobot.washFrequencyOptions.last())

        val serverOnly = DockCapabilities("roborock.vacuum.a294", null, null)
        assertTrue(serverOnly.autoEmptyModes.isEmpty())
        assertFalse(serverOnly.washFrequencyOptions.any { it.mode == 2 })
    }

    @Test
    fun an_s7s_new_feature_info_also_allows_the_auto_empty_mode() {
        // Its server record holds a one-character featureSet; its own answer sets bit 25.
        assertTrue(DockCapabilities("roborock.vacuum.a15", "636084721975295", null).autoEmptyModes.isNotEmpty())
    }

    @Test
    fun beta_page_shows_when_the_robot_advertises_flags_without_a_switch() {
        assertFalse(showsBetaPage(supportedBetaFeatures(null), unlistedAdvertisedFeatures(null)))
        assertTrue(showsBetaPage(supportedBetaFeatures(flags), unlistedAdvertisedFeatures(flags)))
    }
}
