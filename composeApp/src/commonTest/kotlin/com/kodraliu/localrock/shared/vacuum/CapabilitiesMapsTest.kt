package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// app_get_init_status replies read from the two test robots on 2026-11-13.
private const val CURV_FEATURE_SET = "4499197267967999"
private const val CURV_NEW_FEATURE_SET = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO_FEATURE_SET = "2247397454282751"
private const val PRO_NEW_FEATURE_SET = "42BA8F5C7EDAFFFE"

class CapabilitiesMapsTest {
    private val curv = buildMapCapabilities(CURV_FEATURE_SET, CURV_NEW_FEATURE_SET)
    private val pro = buildMapCapabilities(PRO_FEATURE_SET, PRO_NEW_FEATURE_SET)

    @Test
    fun the_curv_has_remove_31_rooms_and_a_floor_plan() {
        assertTrue(buildAdvertisedCapabilities(CURV_NEW_FEATURE_SET).mapEraser)
        assertTrue(MapTool.REMOVE in curv.mapTools)
        assertEquals(31, curv.maxRooms)
        assertTrue(buildAdvertisedCapabilities(CURV_NEW_FEATURE_SET).floorPlanMap)
    }

    @Test
    fun the_qrevo_pro_has_no_remove_15_rooms_and_no_floor_plan() {
        assertFalse(buildAdvertisedCapabilities(PRO_NEW_FEATURE_SET).mapEraser)
        assertFalse(MapTool.REMOVE in pro.mapTools)
        assertEquals(15, pro.maxRooms)
        assertFalse(buildAdvertisedCapabilities(PRO_NEW_FEATURE_SET).floorPlanMap)
    }

    @Test
    fun both_robots_back_up_maps_from_bit_49_of_the_decimal_set() {
        assertTrue(curv.mapBackup)
        assertTrue(pro.mapBackup)
        assertFalse(buildMapCapabilities("1", PRO_NEW_FEATURE_SET).mapBackup)
        assertFalse(buildMapCapabilities(null, null).mapBackup)
    }

    @Test
    fun tools_keep_the_official_order_and_leave_furniture_and_sequence_out() {
        assertEquals(
            listOf(
                MapTool.DIVIDE, MapTool.COMBINE, MapTool.REMOVE, MapTool.NAME_ROOM,
                MapTool.NO_GO, MapTool.THRESHOLD, MapTool.FLOOR_TYPE, MapTool.CARPET,
            ),
            curv.mapTools.toList(),
        )
        assertFalse(MapTool.FURNITURE in curv.mapTools)
        assertFalse(MapTool.SEQUENCE in curv.mapTools)
    }
}
