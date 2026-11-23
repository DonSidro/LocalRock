package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EraserZonesTest {
    // 10 x 2 cells at offset (2, 1): columns 0..4 are room 1, columns 5..9 room 2; one cell is 50 mm.
    private fun map(vararg zones: MapZone, rooms: List<ParsedMapRoom> = emptyList()) = ParsedMap(
        width = 10, height = 2, grid = ByteArray(20) { 128.toByte() }, rooms = rooms, resolution = 0.05f,
        pixelOffsetLeft = 2, pixelOffsetTop = 1, format = MapFormat.LEGACY,
        originalGrid = ByteArray(20) { i -> ((if (i % 10 < 5) 1 else 2) shl 3 or 7).toByte() },
        eraserZones = zones.toList(),
    )

    private fun rect(x0: Int, y0: Int, x1: Int, y1: Int) = MapZone.rect(x0, y0, x1, y1, ZoneKind.ERASER)

    @Test
    fun a_map_without_eraser_zones_has_no_mask() {
        val m = map()
        assertNull(m.erasedCells)
        assertEquals(emptySet(), m.erasedRoomIds)
    }

    @Test
    fun cells_are_erased_by_their_centre_in_millimetres() {
        // x 100..350 mm is columns 0..4, y 50..100 mm is row 0 only.
        val erased = map(rect(100, 50, 350, 100)).erasedCells!!
        assertTrue((0..4).all { erased[it] })
        assertFalse(erased[5])
        assertTrue((10..19).none { erased[it] })
    }

    @Test
    fun a_rotated_quad_erases_only_cells_inside_it() {
        val diamond = MapZone(300, 50, 400, 100, 300, 150, 200, 100, ZoneKind.ERASER)
        val erased = map(diamond).erasedCells!!
        assertTrue(erased[4])
        assertFalse(erased[0])
    }

    @Test
    fun a_room_is_erased_only_when_every_one_of_its_cells_is() {
        assertEquals(setOf(1), map(rect(100, 50, 350, 250)).erasedRoomIds)
        assertEquals(emptySet(), map(rect(100, 50, 350, 100)).erasedRoomIds)
    }

    @Test
    fun erased_cells_belong_to_no_room() {
        val m = map(rect(100, 50, 350, 250), rooms = listOf(ParsedMapRoom(1, "A", 0.2f, 0.5f), ParsedMapRoom(2, "B", 0.8f, 0.5f)))
        assertNull(m.roomIdAtCell(0))
        assertEquals(2, m.roomIdAtCell(7))
        assertNull(m.roomAtNorm(0.1f, 0.5f))
        assertEquals(2, m.roomAtNorm(0.9f, 0.5f)?.id)
    }
}
