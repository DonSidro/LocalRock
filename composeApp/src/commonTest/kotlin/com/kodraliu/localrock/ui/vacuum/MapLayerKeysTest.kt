package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MapLayerKeysTest {
    private val map = ParsedMap(10, 10, ByteArray(100), emptyList(), 0.05f)

    @Test
    fun a_robot_that_moved_leaves_the_floor_and_zone_layers_as_they_were() {
        val moved = map.copy(robotMm = ParsedMapPoint(100, 200), pathMm = listOf(ParsedMapPoint(1, 2), ParsedMapPoint(3, 4)))
        assertEquals(map.floorKey(), moved.floorKey())
        assertEquals(map.restrictionsKey(), moved.restrictionsKey())
    }

    @Test
    fun a_changed_grid_or_carpet_redraws_the_floor() {
        assertNotEquals(map.floorKey(), map.copy(grid = ByteArray(100) { 128.toByte() }).floorKey())
        assertNotEquals(map.floorKey(), map.copy(carpetMask = ByteArray(100) { 1 }).floorKey())
    }
}
