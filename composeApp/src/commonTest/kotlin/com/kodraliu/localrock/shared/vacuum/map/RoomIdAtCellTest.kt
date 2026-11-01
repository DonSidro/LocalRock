package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomIdAtCellTest {

    private val rooms = listOf(ParsedMapRoom(16, "Room 16"), ParsedMapRoom(17, "Room 17"))

    @Test
    fun a_b01_cell_gives_its_segment_id() {
        // 0 outside, 127 wall, 128 floor no room owns, 1-126 the segment.
        val map = ParsedMap(
            width = 5, height = 1,
            grid = byteArrayOf(16, 17, 127, 0, 128.toByte()),
            rooms = rooms, resolution = 0.05f,
        )

        assertEquals(16, map.roomIdAtCell(0))
        assertEquals(17, map.roomIdAtCell(1))
        assertNull(map.roomIdAtCell(2))
        assertNull(map.roomIdAtCell(3))
        assertNull(map.roomIdAtCell(4))
    }

    @Test
    fun a_legacy_cell_gives_the_room_packed_into_its_byte() {
        // (byte >> 3) & 0x1f is the room; low three bits are 7 on floor, 1 on a wall, 0 outside.
        val roomFive = (5 shl 3) or 7
        val roomNine = (9 shl 3) or 7
        val wall = 1
        val map = ParsedMap(
            width = 4, height = 1,
            grid = byteArrayOf(128.toByte(), 128.toByte(), 127, 0),
            rooms = rooms, resolution = 0.05f,
            originalGrid = byteArrayOf(roomFive.toByte(), roomNine.toByte(), wall.toByte(), 0),
        )

        assertEquals(5, map.roomIdAtCell(0))
        assertEquals(9, map.roomIdAtCell(1))
        assertNull(map.roomIdAtCell(2))
        assertNull(map.roomIdAtCell(3))
    }

    @Test
    fun a_legacy_floor_cell_with_room_zero_belongs_to_no_room() {
        val map = ParsedMap(
            width = 1, height = 1,
            grid = byteArrayOf(128.toByte()),
            rooms = rooms, resolution = 0.05f,
            originalGrid = byteArrayOf(2),
        )

        assertNull(map.roomIdAtCell(0))
    }

    @Test
    fun a_cell_off_the_grid_belongs_to_no_room() {
        val map = ParsedMap(width = 1, height = 1, grid = byteArrayOf(16), rooms = rooms, resolution = 0.05f)

        assertNull(map.roomIdAtCell(-1))
        assertNull(map.roomIdAtCell(1))
    }

    @Test
    fun it_agrees_with_the_room_a_tap_picks_on_a_cell_that_has_one() {
        val map = ParsedMap(
            width = 2, height = 1,
            grid = byteArrayOf(16, 17),
            rooms = rooms, resolution = 0.05f,
        )

        // Cell 1 spans x 0.5-1.0; its centre is x 0.75. Row 0 is the only row.
        assertEquals(map.roomIdAtCell(1), map.roomAtNorm(0.75f, 0.5f)?.id)
    }
}
