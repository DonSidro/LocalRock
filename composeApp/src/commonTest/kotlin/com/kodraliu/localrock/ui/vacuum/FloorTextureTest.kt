package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FloorTextureTest {
    @Test
    fun wood_direction_picks_the_grain_axis() {
        assertEquals(FloorPattern.WOOD_ALONG_Y, floorPattern(3, 90))
        assertEquals(FloorPattern.WOOD_ALONG_X, floorPattern(3, 0))
        assertEquals(FloorPattern.WOOD_ALONG_X, floorPattern(3, null))
    }

    @Test
    fun other_materials() {
        assertEquals(FloorPattern.TILE, floorPattern(4, -1))
        assertEquals(FloorPattern.NONE, floorPattern(0, null))
        assertEquals(FloorPattern.NONE, floorPattern(null, null))
        assertEquals(FloorPattern.NONE, floorPattern(99, null))
    }

    @Test
    fun vertical_planks_vary_across_x_only() {
        val p = FloorPattern.WOOD_ALONG_Y
        assertEquals(textureShade(p, 10, 0), textureShade(p, 10, 57))
        assertNotEquals(textureShade(p, 9, 0), textureShade(p, 10, 0))
    }

    @Test
    fun horizontal_planks_vary_across_y_only() {
        val p = FloorPattern.WOOD_ALONG_X
        assertEquals(textureShade(p, 0, 10), textureShade(p, 57, 10))
        assertNotEquals(textureShade(p, 0, 9), textureShade(p, 0, 10))
    }

    @Test
    fun seams_are_darker_than_boards() {
        assertTrue(textureShade(FloorPattern.WOOD_ALONG_Y, 3, 5) < textureShade(FloorPattern.WOOD_ALONG_Y, 4, 5))
        assertTrue(textureShade(FloorPattern.TILE, 8, 3) < textureShade(FloorPattern.TILE, 9, 3))
    }

    @Test
    fun no_pattern_leaves_the_tint_alone() {
        assertEquals(0f, textureShade(FloorPattern.NONE, 7, 7))
    }
}
