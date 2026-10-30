package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.graphics.Color
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapRendererTest {

    private fun mapOf(width: Int, height: Int) =
        ParsedMap(width = width, height = height, grid = ByteArray(width * height), rooms = emptyList(), resolution = 0.05f)

    @Test
    fun a_selected_room_keeps_its_hue_but_comes_up_bright() {
        // The dark theme's blue-ish room tint: blue is its strongest channel, and stays so when picked.
        val muted = Color(0xFF1F3140)
        val vivid = vividOf(muted)

        assertTrue(vivid.blue > vivid.green && vivid.green > vivid.red, "hue should stay blue")
        assertTrue(maxOf(vivid.red, vivid.green, vivid.blue) > 0.9f, "should be bright")
        assertTrue(vivid.blue > muted.blue + 0.4f, "should be far brighter than the muted tint")
    }

    @Test
    fun different_room_tints_stay_different_colours_when_picked() {
        val tints = listOf(0xFF1E3A38, 0xFF1F3140, 0xFF24382A, 0xFF3A3328, 0xFF2E2A3B, 0xFF38292A)
            .map { vividOf(Color(it)) }

        // A warm tint must not come out the same as a cool one, or every pick would look alike.
        assertEquals(tints.size, tints.toSet().size)
        assertTrue(tints[3].red > tints[3].blue, "the brown-orange tint should stay warm")
        assertTrue(tints[1].blue > tints[1].red, "the blue tint should stay cool")
    }

    @Test
    fun a_grey_tint_has_no_hue_to_keep_so_it_just_comes_up_lighter() {
        val vivid = vividOf(Color(0xFF444444))

        assertEquals(vivid.red, vivid.green)
        assertEquals(vivid.green, vivid.blue)
        assertTrue(vivid.red > 0.5f)
    }

    @Test
    fun a_typical_map_is_drawn_at_three_times_the_grid() {
        assertEquals(3, supersampleFor(mapOf(300, 400)))
        assertEquals(3, supersampleFor(mapOf(800, 800)))
    }

    @Test
    fun a_large_map_is_drawn_smaller_so_it_cannot_run_the_device_out_of_memory() {
        // 2400 / 1000 = 2, and anything past 2400 cells a side is drawn at the grid's own size.
        assertEquals(2, supersampleFor(mapOf(1000, 10)))
        assertEquals(1, supersampleFor(mapOf(2400, 4)))
        assertEquals(1, supersampleFor(mapOf(5000, 2)))
    }
}
