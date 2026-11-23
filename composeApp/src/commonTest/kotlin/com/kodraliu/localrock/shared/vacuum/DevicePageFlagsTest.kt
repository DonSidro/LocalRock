package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// new_feature_info_str read from the two test robots with app_get_init_status (2026-11-13).
private const val CURV = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO = "42BA8F5C7EDAFFFE"

class DevicePageFlagsTest {

    @Test
    fun the_curv_has_vac_followed_by_mop_with_repeat_no_pure_mop_and_halfway_docking() {
        val flags = buildAdvertisedCapabilities(CURV)
        assertTrue(flags.cleanThenMop)
        assertTrue(flags.cleanThenMopRepeat)
        assertTrue(flags.forbidPureMop)
        assertTrue(flags.midwayDock)
        assertTrue(devicePageFlags(CURV).routineTabKnown)
    }

    @Test
    fun the_qrevo_pro_has_none_of_them_but_allows_pure_mop() {
        val flags = buildAdvertisedCapabilities(PRO)
        assertFalse(flags.cleanThenMop)
        assertFalse(flags.cleanThenMopRepeat)
        assertFalse(flags.forbidPureMop)
        assertFalse(flags.midwayDock)
    }

    @Test
    fun clean_then_mop_needs_bit_114_only_on_fcc_robots() {
        // Bit 93 alone: hex digit 23 from the end, value 2.
        val only93 = "2" + "0".repeat(23)
        assertFalse(buildAdvertisedCapabilities(only93, fcc = true).cleanThenMop)
        assertFalse(buildAdvertisedCapabilities(only93, fcc = null).cleanThenMop)
        assertTrue(buildAdvertisedCapabilities(only93, fcc = false).cleanThenMop)
        // Both test robots report featureset 1; the Curv has 93 and 114, the Pro neither.
        assertTrue(buildAdvertisedCapabilities(CURV, fcc = true).cleanThenMop)
        assertFalse(buildAdvertisedCapabilities(PRO, fcc = false).cleanThenMop)
        assertTrue(buildCapabilities(null, null, null, only93, robotIsFcc = false).advertised.cleanThenMop)
    }

    @Test
    fun capabilities_carry_the_device_flags() {
        val caps = buildCapabilities("roborock.vacuum.a245", null, null, CURV)
        assertTrue(caps.advertised.cleanThenMop)
        assertTrue(caps.advertised.midwayDock)
        assertTrue(caps.advertised.forbidPureMop)
    }

    @Test
    fun routine_tab_follows_the_dynamic_zone_bits() {
        val all = listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE)
        // Both test robots set bits 25 and 26 (low word 0x7E......).
        assertEquals(all, visibleMainTabs(buildCapabilities(null, null, null, PRO), 0))
        assertEquals(all, visibleMainTabs(buildCapabilities(null, null, null, CURV), 0))
        // Bit 26 alone (0x04000000) is not enough.
        assertFalse(devicePageFlags("04000000").routineTabKnown)
        assertTrue(devicePageFlags("06000000").routineTabKnown)
    }

    @Test
    fun routine_tab_still_shows_without_feature_bits_once_routines_exist() {
        val caps = buildCapabilities(null, null, null, null)
        assertEquals(listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE), visibleMainTabs(caps, 0))
        assertEquals(listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE), visibleMainTabs(caps, 2))
    }

    @Test
    fun back_type_is_parsed_from_the_status() {
        val status = Json { ignoreUnknownKeys = true }.decodeFromString(VacuumStatus.serializer(), """{"state":26,"back_type":4}""")
        assertEquals(4, status.backType)
    }
}
