package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.DIRTY_REPLENISH_CLEAN_FEATURE_BIT
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.secondaryFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirtMapTest {
    @Test
    fun dirtBitsFollowThePlugin() {
        assertEquals(DIRT_OUTSIDE, dirtLevelOf(0))
        assertEquals(DIRT_OUTSIDE, dirtLevelOf(1)) // carpet only
        assertEquals(DIRT_DEEP, dirtLevelOf(2))
        assertEquals(DIRT_LIGHT, dirtLevelOf(4))
        assertEquals(DIRT_DEEP, dirtLevelOf(2 or 4 or 1)) // bit 1 wins
        assertEquals(DIRT_OUTSIDE, dirtLevelOf(2 or (1 shl 3))) // bits 3-4 = 1 skipped
        assertEquals(DIRT_OUTSIDE, dirtLevelOf(4 or (2 shl 3)))
        assertEquals(DIRT_LIGHT, dirtLevelOf(4 or (3 shl 3)))
    }

    private fun map(carpet: ByteArray?): ParsedMap {
        // 4x3 grid, row 0 at the bottom: wall ring with two floor cells in the middle row.
        val w = 127.toByte()
        val f = 128.toByte()
        val o: Byte = 0
        val grid = byteArrayOf(
            o, w, w, o,
            o, f, f, w,
            o, w, w, o,
        )
        return ParsedMap(width = 4, height = 3, grid = grid, rooms = emptyList(), resolution = 0.05f, carpetMask = carpet)
    }

    @Test
    fun noLayerWithoutBlockOrDirt() {
        assertNull(map(null).dirtLayer())
        assertNull(map(ByteArray(12) { 1 }).dirtLayer())
        assertNull(map(ByteArray(5)).dirtLayer())
    }

    @Test
    fun layerClassifiesAndCrops() {
        val carpet = ByteArray(12)
        carpet[5] = 4
        carpet[6] = 2
        val layer = assertNotNull(map(carpet).dirtLayer())
        assertEquals(DIRT_LIGHT, layer.cells[5])
        assertEquals(DIRT_DEEP, layer.cells[6])
        assertEquals(DIRT_WALL, layer.cells[1])
        assertEquals(DIRT_OUTSIDE, layer.cells[0])
        assertEquals(1, layer.left)
        assertEquals(3, layer.right)
        assertEquals(0, layer.bottom)
        assertEquals(2, layer.top)
        assertEquals(3, layer.cropWidth)
        assertEquals(3, layer.cropHeight)
    }

    @Test
    fun toggleNeedsGateAndDirt() {
        val carpet = ByteArray(12).also { it[5] = 4 }
        val layer = map(carpet).dirtLayer()
        assertTrue(showsDirtMapToggle(true, layer))
        assertFalse(showsDirtMapToggle(false, layer))
        assertFalse(showsDirtMapToggle(true, null))
    }

    @Test
    fun gateIsBit34() {
        assertEquals(34, DIRTY_REPLENISH_CLEAN_FEATURE_BIT)
        assertFalse(secondaryFlags(null, patrol = false).dirtMap)
        assertTrue(secondaryFlags("400000000", patrol = false).dirtMap)
        assertFalse(secondaryFlags("300000000", patrol = false).dirtMap)
    }
}
