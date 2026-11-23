package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// app_get_init_status replies read from the two test robots (new_feature_info and new_feature_info_str).
private const val CURV_MODEL = "roborock.vacuum.a245"
private const val CURV_FEATURE_SET = "4499197267967999"
private const val CURV_NEW_FEATURE_SET = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO_MODEL = "roborock.vacuum.a101"
private const val PRO_FEATURE_SET = "2247397454282751"
private const val PRO_NEW_FEATURE_SET = "42BA8F5C7EDAFFFE"

class CapabilitiesTest {
    private val curv = buildCapabilities(CURV_MODEL, CURV_FEATURE_SET, CURV_NEW_FEATURE_SET, null)
    private val pro = buildCapabilities(PRO_MODEL, PRO_FEATURE_SET, PRO_NEW_FEATURE_SET, null)

    @Test
    fun the_curv_2_flow_has_matter_and_the_qrevo_pro_does_not() {
        assertTrue(curv.matter)
        assertFalse(pro.matter)
    }

    @Test
    fun the_secondary_page_flags_follow_the_advertised_bits() {
        assertTrue(curv.firmwareUpdates && pro.firmwareUpdates)
        assertEquals(newFeatureBit(CURV_NEW_FEATURE_SET, 48), curv.cruiseHistory)
        assertEquals(newFeatureBit(CURV_NEW_FEATURE_SET, 34), curv.dirtMap)
        assertEquals(newFeatureBit(PRO_NEW_FEATURE_SET, 6), pro.autoFirmwareUpdates)
        assertFalse(buildCapabilities(null, null, null, null).dirtMap)
    }

    @Test
    fun only_the_curv_has_the_roller_mop() {
        assertTrue(curv.advertised.rollerMop)
        assertFalse(pro.advertised.rollerMop)
    }

    @Test
    fun the_floor_pattern_row_follows_its_feature_bit() {
        assertTrue(curv.hasFloorPattern)
        assertFalse(pro.hasFloorPattern)
    }

    @Test
    fun settings_rows_both_robots_show() {
        for (c in listOf(curv, pro)) {
            assertTrue(c.hasCarpetSettings)
            assertTrue(c.hasFloorSettings)
            assertTrue(c.hasDockSettings)
            assertTrue(c.offlineMap)
            assertTrue(c.customDnd)
            assertFalse(c.multiSlotDnd)
            assertTrue(c.remoteControl)
            assertTrue(c.pinNGo)
            assertTrue(c.firmwareUpdates)
        }
    }

    @Test
    fun flags_without_a_known_gate_stay_off() {
        for (c in listOf(curv, pro)) {
            assertFalse(c.hasAutoEmptySettings)
            assertFalse(c.hasObstacleSettings)
        }
    }

    @Test
    fun beta_features_match_the_existing_helpers() {
        assertEquals(supportedBetaFeatures(CURV_NEW_FEATURE_SET), curv.betaFeatures)
        assertEquals(unlistedAdvertisedFeatures(CURV_NEW_FEATURE_SET), curv.unlistedFeatures)
        assertTrue(curv.showsBetaFeatures)
        assertEquals(emptyList(), pro.unlistedFeatures.map { it.bit })
        assertFalse(pro.showsBetaFeatures)
    }

    @Test
    fun dock_capabilities_use_the_model_and_feature_sets() {
        assertEquals(listOf(10800, 14400, 18000), curv.dock.dryTimeOptions(null).map { it.code })
        assertEquals(listOf(7200, 10800, 14400), pro.dock.dryTimeOptions(null).map { it.code })
    }

    @Test
    fun the_robots_own_feature_set_wins_over_the_servers() {
        val local = buildCapabilities(CURV_MODEL, null, null, CURV_NEW_FEATURE_SET)
        assertTrue(local.matter)
        val stale = buildCapabilities(CURV_MODEL, null, PRO_NEW_FEATURE_SET, CURV_NEW_FEATURE_SET)
        assertTrue(stale.matter)
        val none = buildCapabilities(null, null, null, null)
        assertFalse(none.matter)
        assertFalse(none.showsBetaFeatures)
    }
}
