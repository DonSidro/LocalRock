package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloorPlanFitTest {
    private val w = 40
    private val h = 40

    /** A grid with floor (128) wherever [floor] says so, in the grid's own (x, y) with row 0 at the bottom. */
    private fun grid(floor: (x: Int, y: Int) -> Boolean) =
        ByteArray(w * h) { i -> if (floor(i % w, i / w)) 128.toByte() else 0 }

    /** A rectangle over grid cells x0 until x1, y0 until y1, in drawn coordinates. */
    private fun box(x0: Int, y0: Int, x1: Int, y1: Int) = FloorPlanShape(
        segment = 1,
        fill = listOf(
            Offset(x0.toFloat(), (h - y1).toFloat()), Offset(x1.toFloat(), (h - y1).toFloat()),
            Offset(x1.toFloat(), (h - y0).toFloat()), Offset(x0.toFloat(), (h - y0).toFloat()),
        ),
        outer = emptyList(), inner = emptyList(), door = emptyList(),
    )

    @Test
    fun an_outline_that_matches_the_floor_fits() {
        val g = grid { x, y -> x in 5 until 25 && y in 5 until 20 }
        assertTrue(floorPlanFitsGrid(listOf(box(5, 5, 25, 20)), g, w, h))
    }

    @Test
    fun a_box_around_a_room_that_fills_only_part_of_it_does_not_fit() {
        // The floor is the bottom third of the box, as with the Qrevo Curv 2 Flow's outlines.
        val g = grid { x, y -> x in 5 until 35 && y in 5 until 15 }
        assertFalse(floorPlanFitsGrid(listOf(box(5, 5, 35, 35)), g, w, h))
    }

    @Test
    fun one_bad_room_among_good_ones_is_enough_to_fall_back() {
        val g = grid { x, y -> (x in 0 until 10 && y in 0 until 10) || (x in 20 until 40 && y in 20 until 25) }
        val good = box(0, 0, 10, 10)
        val bad = box(20, 20, 40, 40)
        assertFalse(floorPlanFitsGrid(listOf(good, bad), g, w, h))
    }

    @Test
    fun walls_count_as_part_of_the_room() {
        val g = ByteArray(w * h) { i ->
            val x = i % w; val y = i / w
            when {
                x !in 4..25 || y !in 4..20 -> 0
                x == 4 || x == 25 || y == 4 || y == 20 -> 127.toByte()
                else -> 128.toByte()
            }
        }
        assertTrue(floorPlanFitsGrid(listOf(box(4, 4, 26, 21)), g, w, h))
    }

    @Test
    fun a_small_outline_is_not_judged() {
        val g = grid { _, _ -> false }
        assertTrue(floorPlanFitsGrid(listOf(box(0, 0, 7, 7)), g, w, h))
    }

    @Test
    fun an_l_shaped_room_with_a_matching_outline_fits() {
        val g = grid { x, y -> (x in 0 until 30 && y in 0 until 10) || (x in 0 until 10 && y in 10 until 30) }
        val l = FloorPlanShape(
            segment = 1,
            fill = listOf(Offset(0f, 10f), Offset(10f, 10f), Offset(10f, 30f), Offset(30f, 30f), Offset(30f, 40f), Offset(0f, 40f)),
            outer = emptyList(), inner = emptyList(), door = emptyList(),
        )
        assertTrue(floorPlanFitsGrid(listOf(l), g, w, h))
    }
}
