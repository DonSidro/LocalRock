package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MapTool
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanPoint
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanZone
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManageMapsTest {
    @Test
    fun tiles_follow_the_official_order_whatever_the_set_order() {
        val tiles = manageMapTiles(setOf(MapTool.CARPET, MapTool.DIVIDE, MapTool.NO_GO, MapTool.REMOVE, MapTool.COMBINE))
        assertEquals(listOf(MapTool.DIVIDE, MapTool.COMBINE, MapTool.REMOVE, MapTool.NO_GO, MapTool.CARPET), tiles)
    }

    @Test
    fun tile_and_editor_labels_use_the_official_words() {
        assertEquals("Divide", mapToolTitle(MapTool.DIVIDE))
        assertEquals("Combine", mapToolTitle(MapTool.COMBINE))
        assertEquals("Remove", mapToolTitle(MapTool.REMOVE))
        assertEquals("Name the Room", mapToolTitle(MapTool.NAME_ROOM))
        assertEquals("Cleaning Area Management", mapToolTitle(MapTool.NO_GO))
        assertEquals("Threshold", mapToolTitle(MapTool.THRESHOLD))
        assertEquals("Floor Type", mapToolTitle(MapTool.FLOOR_TYPE))
        assertEquals("Carpet", mapToolTitle(MapTool.CARPET))
    }

    @Test
    fun tools_are_disabled_only_once_the_robot_says_it_has_no_saved_map() {
        assertTrue(mapToolsEnabled(floorMapsLoaded = false, savedMaps = 0, mapLoaded = true))
        assertTrue(mapToolsEnabled(floorMapsLoaded = true, savedMaps = 1, mapLoaded = true))
        assertFalse(mapToolsEnabled(floorMapsLoaded = true, savedMaps = 0, mapLoaded = true))
        assertFalse(mapToolsEnabled(floorMapsLoaded = true, savedMaps = 1, mapLoaded = false))
    }

    @Test
    fun the_menu_offers_backup_and_restore_by_support_and_switch_map_only_with_several_floors() {
        val floor = FloorMap("Map1", 0, savedAt = 1L, backupAt = null)
        assertEquals(ManageMapsMenu(backup = true, restore = false, switchMap = false), manageMapsMenu(true, floor, 1, false))
        assertEquals(ManageMapsMenu(true, true, false), manageMapsMenu(true, floor.copy(backupAt = 2L), 1, null))
        assertEquals(ManageMapsMenu(false, false, false), manageMapsMenu(false, floor.copy(backupAt = 2L), 1, null))
        assertEquals(ManageMapsMenu(false, false, true), manageMapsMenu(true, null, 2, true))
        assertTrue(manageMapsMenu(false, floor, 1, true).switchMap)
    }

    @Test
    fun map_details_round_trip_and_default_to_all_on() {
        assertEquals(MapDisplay(), MapDisplay.decode(null))
        val off = MapDisplay(roomNames = false, floorType = true, furniture = false, obstacle = true, homeLayout = false)
        assertEquals(off, MapDisplay.decode(off.encode()))
        assertEquals(0, MapDisplay(false, false, false, false, false).encode())
        assertEquals(31, MapDisplay().encode())
    }

    @Test
    fun home_layout_needs_the_bit_and_a_map_with_floor_plan_polygons() {
        val zone = FloorPlanZone(1, listOf(FloorPlanPoint(0, 0, 1), FloorPlanPoint(5, 0, 1), FloorPlanPoint(5, 2, 1)))
        val plain = ParsedMap(width = 2, height = 2, grid = ByteArray(4), rooms = emptyList(), resolution = 0.05f, format = MapFormat.LEGACY)
        val planned = plain.copy(zoneLines = listOf(zone))
        assertTrue(hasHomeLayout(true, planned))
        assertFalse(hasHomeLayout(true, plain))
        assertFalse(hasHomeLayout(false, planned))
        assertFalse(hasHomeLayout(true, null))
    }

    @Test
    fun a_cruise_pin_head_sits_above_its_tip() {
        assertEquals(Offset(40f, 30f), cruisePinHead(Offset(40f, 64f), 34f))
    }
}
