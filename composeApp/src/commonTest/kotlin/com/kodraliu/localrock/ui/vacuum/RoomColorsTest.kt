package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals

class RoomColorsTest {
    private fun mapOf(vararg rooms: Int): ParsedMap {
        val raw = ByteArray(rooms.size) { if (rooms[it] < 0) 1 else ((rooms[it] shl 3) or 7).toByte() }
        return ParsedMap(
            width = rooms.size, height = 1, grid = ByteArray(rooms.size) { if (rooms[it] < 0) 127.toByte() else 128.toByte() },
            rooms = emptyList(), resolution = 0.05f, originalGrid = raw,
        )
    }

    @Test
    fun rooms_that_touch_get_different_slots() {
        val slots = roomColorSlots(mapOf(1, 2, 3, 1), 4)
        assertNotEquals(slots[1], slots[2])
        assertNotEquals(slots[2], slots[3])
        assertNotEquals(slots[3], slots[1])
    }

    @Test
    fun rooms_split_by_a_wall_still_count_as_touching() {
        val slots = roomColorSlots(mapOf(1, 1, -1, -1, 2, 2), 4)
        assertNotEquals(slots[1], slots[2])
    }

    @Test
    fun a_cell_a_room_claims_outside_the_walls_is_not_a_room() {
        val raw = byteArrayOf(((1 shl 3) or 7).toByte(), (2 shl 3).toByte())
        val map = ParsedMap(2, 1, byteArrayOf(128.toByte(), 0), emptyList(), 0.05f, originalGrid = raw)
        assertEquals(setOf(1), roomColorSlots(map, 4).keys)
    }
}
