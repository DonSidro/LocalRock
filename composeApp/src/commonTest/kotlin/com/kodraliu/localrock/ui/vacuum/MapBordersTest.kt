package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapBordersTest {
    // '.' is empty, a digit is a floor cell of that room, 'w' is a wall cell with no room id.
    private class Cells(val grid: ByteArray, val raw: ByteArray, val w: Int, val h: Int)

    private fun cells(vararg rows: String): Cells {
        val h = rows.size
        val w = rows[0].length
        val grid = ByteArray(w * h)
        val raw = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val c = rows[y][x]
            val i = y * w + x
            when {
                c.isDigit() -> { grid[i] = 128.toByte(); raw[i] = (((c - '0') shl 3) or 7).toByte() }
                c == 'w' -> { grid[i] = 127.toByte(); raw[i] = 1 }
            }
        }
        return Cells(grid, raw, w, h)
    }

    private fun borders(c: Cells) = roomBorderEdges(c.grid, c.raw, c.w, c.h)

    @Test
    fun two_rooms_side_by_side_share_one_merged_vertical_run() {
        val runs = borders(cells("111222", "111222", "111222", "111222", "111222", "111222"))
        assertEquals(listOf(BorderRun(3, 0, 3, 6)), runs)
    }

    @Test
    fun a_room_does_not_border_itself() {
        assertTrue(borders(cells("111111", "111111", "111111", "111111", "111111", "111111")).isEmpty())
    }

    @Test
    fun a_room_split_in_two_gets_a_run_at_the_split_only() {
        val runs = borders(cells("111111", "111111", "111111", "222222", "222222", "222222"))
        // Data row 3 is the boundary; drawn Y is flipped, so it lands at 6 - 3.
        assertEquals(listOf(BorderRun(0, 3, 6, 3)), runs)
    }

    @Test
    fun a_notch_of_another_room_is_bordered_on_each_touching_side() {
        val runs = borders(cells("111111", "112211", "112211", "111111", "111111", "111111"))
        val lengths = runs.sumOf { kotlin.math.abs(it.x1 - it.x0) + kotlin.math.abs(it.y1 - it.y0) }
        // A 2x2 block inside a room has a perimeter of 8.
        assertEquals(8, lengths)
        assertEquals(4, runs.size)
    }

    @Test
    fun empty_space_and_roomless_walls_never_get_a_border() {
        val runs = borders(cells("11.222", "11.222", "11w222", "11.222", "11.222", "11.222"))
        assertTrue(runs.isEmpty())
    }

    @Test
    fun separate_stretches_on_one_line_stay_separate_runs() {
        val runs = borders(cells("1212", "1212", "1111", "1212"))
        assertEquals(2, runs.count { it.x0 == 1 && it.x1 == 1 })
    }

    @Test
    fun a_wall_cell_takes_its_own_room_or_a_neighbouring_floor_room() {
        val c = cells("12w", "1w3")
        c.raw[2] = ((2 shl 3) or 1).toByte()
        assertEquals(2, flatWallRoom(c.grid, c.raw, c.w, c.h, 2, 0))
        // The lower wall has no id, so it takes a floor neighbour (left, room 1).
        assertEquals(1, flatWallRoom(c.grid, c.raw, c.w, c.h, 1, 1))
        val lone = cells("w")
        assertNull(flatWallRoom(lone.grid, lone.raw, 1, 1, 0, 0))
    }

    @Test
    fun the_outline_stroke_stays_inside_the_plan_and_is_thin() {
        val grid = ByteArray(16) { 128.toByte() }
        val loops = mapOutlineLoops(ParsedMap(4, 4, grid, emptyList(), 0.05f))
        assertTrue(loops[0].all { it.x in 0f..4f && it.y in 0f..4f })
        assertTrue(MAP_OUTLINE_WIDTH_CELLS in 0.05f..1f)
        assertTrue(MAP_ROOM_BORDER_WIDTH_CELLS <= MAP_OUTLINE_WIDTH_CELLS)
    }
}
