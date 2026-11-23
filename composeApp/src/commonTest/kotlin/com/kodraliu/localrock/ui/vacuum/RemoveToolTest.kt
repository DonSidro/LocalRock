package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoveToolTest {
    @Test
    fun the_body_matches_the_official_apps_capture() {
        val zone = MapZone.rect(19673, 32139, 22756, 35541, ZoneKind.ERASER)
        assertEquals(
            """{"map_index":0,"data":[[0,19673,35541,22756,35541,22756,32139,19673,32139]]}""",
            beautifyBlocksParams(0, listOf(zone)).toString(),
        )
    }

    @Test
    fun an_empty_list_clears_every_area_and_ids_count_from_zero() {
        assertEquals("""{"map_index":1,"data":[]}""", beautifyBlocksParams(1, emptyList()).toString())
        val a = MapZone.rect(100, 100, 200, 200, ZoneKind.ERASER)
        val b = MapZone.rect(300, 300, 400, 500, ZoneKind.ERASER)
        assertEquals(
            """{"map_index":0,"data":[[0,100,200,200,200,200,100,100,100],[1,300,500,400,500,400,300,300,300]]}""",
            beautifyBlocksParams(0, listOf(a, b)).toString(),
        )
    }

    @Test
    fun corners_read_in_any_order_are_sent_as_bounds() {
        val shuffled = MapZone(200, 100, 100, 100, 100, 200, 200, 200, ZoneKind.ERASER)
        assertEquals("""{"map_index":0,"data":[[0,100,200,200,200,200,100,100,100]]}""", beautifyBlocksParams(0, listOf(shuffled)).toString())
    }

    @Test
    fun an_area_over_or_beside_the_dock_is_too_close() {
        val area = MapZone.rect(1000, 1000, 2000, 2000, ZoneKind.ERASER)
        assertTrue(eraserTooCloseToDock(listOf(area), ParsedMapPoint(1500, 1500)))
        assertTrue(eraserTooCloseToDock(listOf(area), ParsedMapPoint(2000 + ERASER_DOCK_CLEARANCE_MM, 1500)))
        assertFalse(eraserTooCloseToDock(listOf(area), ParsedMapPoint(2001 + ERASER_DOCK_CLEARANCE_MM, 1500)))
        assertFalse(eraserTooCloseToDock(listOf(area), null))
    }

    @Test
    fun the_readback_matches_by_bounds_in_any_order() {
        val a = MapZone.rect(100, 100, 200, 200, ZoneKind.ERASER)
        val b = MapZone.rect(300, 300, 400, 500, ZoneKind.ERASER)
        assertTrue(eraserZonesMatch(listOf(a, b), listOf(b, a)))
        assertTrue(eraserZonesMatch(listOf(a), listOf(MapZone.rect(101, 99, 200, 201, ZoneKind.ERASER))))
        assertFalse(eraserZonesMatch(listOf(a, b), listOf(a)))
        assertFalse(eraserZonesMatch(listOf(a), listOf(MapZone.rect(150, 100, 200, 200, ZoneKind.ERASER))))
        assertTrue(eraserZonesMatch(emptyList(), emptyList()))
    }
}
