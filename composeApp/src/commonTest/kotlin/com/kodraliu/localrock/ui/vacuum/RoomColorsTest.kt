package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class RoomColorsTest {
    /** One row of cells: a positive number is that room's floor, a negative one is that room's wall. */
    private fun row(vararg cells: Int): ByteArray = ByteArray(cells.size) {
        val c = cells[it]
        if (c > 0) ((c shl 3) or 7).toByte() else (((-c) shl 3) or 1).toByte()
    }

    private fun colours(vararg cells: Int) = appRoomColours(row(*cells), cells.size, 1)

    @Test
    fun the_biggest_room_takes_the_first_colour_and_the_rest_avoid_their_neighbours() {
        val c = colours(1, 1, 1, 2, 2, 3)
        assertEquals(1, c[1])
        assertEquals(2, c[2])
        assertEquals(3, c[3])
    }

    @Test
    fun a_wall_cell_carries_its_rooms_id_so_rooms_touch_across_it() {
        val c = colours(1, 1, -1, 2)
        assertEquals(1, c[1])
        assertEquals(2, c[2])
    }

    @Test
    fun a_colour_nobody_got_is_shared_out_so_more_colours_show() {
        // Rooms 1 and 2 do not touch, so both would take the first colour; the second goes to the next.
        val c = colours(1, 1, 1, 0, 0, 2)
        assertEquals(1, c[1])
        assertEquals(2, c[2])
    }

    // A Qrevo Pro's map. Its rooms get the colours the Roborock app shows for them: segment 21 is the
    // Living Room, 16 the Hall, 20 the Dining room, 18 the unnamed room, and so on.
    @Test
    fun the_rooms_of_a_real_map_get_the_colours_the_roborock_app_gives_them() {
        val sizes = mapOf(21 to 11633, 24 to 6341, 22 to 5995, 16 to 5132, 23 to 5018, 20 to 4719, 17 to 4697, 18 to 3733, 19 to 3257, 26 to 1218, 25 to 1196)
        val pairs = listOf(
            16 to 17, 16 to 19, 16 to 20, 16 to 21, 16 to 22, 16 to 23, 16 to 24, 16 to 25, 16 to 26,
            17 to 18, 17 to 23, 18 to 23, 19 to 20, 20 to 21, 22 to 24,
        )
        val exists = BooleanArray(32)
        val pixels = IntArray(32)
        for ((room, size) in sizes) { exists[room] = true; pixels[room] = size }
        val touches = Array(32) { BooleanArray(32) }
        for ((a, b) in pairs) { touches[a][b] = true; touches[b][a] = true }

        val result = colourRooms(exists, pixels, touches)

        val expected = mapOf(21 to 1, 17 to 1, 19 to 1, 25 to 1, 26 to 1, 22 to 1, 16 to 2, 20 to 3, 23 to 3, 24 to 3, 18 to 4)
        for ((room, colour) in expected) assertEquals(colour, result[room], "room $room")
    }
}
