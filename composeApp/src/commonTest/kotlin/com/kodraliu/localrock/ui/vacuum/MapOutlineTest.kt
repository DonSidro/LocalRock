package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapOutlineTest {
    private fun mapOf(width: Int, height: Int, filled: Set<Pair<Int, Int>>): ParsedMap {
        val grid = ByteArray(width * height)
        for ((x, y) in filled) grid[y * width + x] = 128.toByte()
        return ParsedMap(width, height, grid, emptyList(), 0.05f)
    }

    @Test
    fun a_block_has_one_loop_inside_its_own_bounds() {
        val loops = mapOutlineLoops(mapOf(4, 3, (0..3).flatMap { x -> (0..2).map { y -> x to y } }.toSet()), smoothing = 0)
        assertEquals(1, loops.size)
        assertEquals(4, loops[0].size)
        assertEquals(0f, loops[0].minOf { it.x })
        assertEquals(4f, loops[0].maxOf { it.x })
    }

    @Test
    fun a_gap_inside_the_plan_gets_its_own_loop() {
        val all = (0..4).flatMap { x -> (0..4).map { y -> x to y } }.toSet() - (2 to 2)
        assertEquals(2, mapOutlineLoops(mapOf(5, 5, all), smoothing = 0).size)
    }

    @Test
    fun smoothing_rounds_corners_inside_the_bounds() {
        val loops = mapOutlineLoops(mapOf(4, 4, (0..3).flatMap { x -> (0..3).map { y -> x to y } }.toSet()))
        assertTrue(loops[0].size > 4)
        assertTrue(loops[0].all { it.x in 0f..4f && it.y in 0f..4f })
    }
}
