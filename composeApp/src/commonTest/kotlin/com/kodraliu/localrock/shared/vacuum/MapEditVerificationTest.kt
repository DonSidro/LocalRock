package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapEditVerificationTest {

    private val rect = MapZone.rect(20000, 20000, 21000, 22000, ZoneKind.NO_GO)
    private val other = MapZone.rect(30000, 30000, 31000, 31000, ZoneKind.NO_GO)
    private val wall = VirtualWall(10000, 10000, 12000, 10000)

    private fun map(noGo: List<MapZone> = emptyList(), noMop: List<MapZone> = emptyList(), walls: List<VirtualWall> = emptyList()) =
        ParsedMap(
            width = 1, height = 1, grid = ByteArray(1), rooms = emptyList(), resolution = 0.05f,
            noGoZones = noGo, noMopZones = noMop, virtualWalls = walls, format = MapFormat.LEGACY,
        )

    @Test
    fun exact_match() {
        assertTrue(restrictionsMatch(map(listOf(rect), walls = listOf(wall)), listOf(rect), emptyList(), listOf(wall)))
    }

    @Test
    fun order_and_corner_order_do_not_matter() {
        val reordered = MapZone(rect.x2, rect.y2, rect.x3, rect.y3, rect.x0, rect.y0, rect.x1, rect.y1, ZoneKind.NO_GO)
        val flippedWall = VirtualWall(wall.x1, wall.y1, wall.x0, wall.y0)
        assertTrue(restrictionsMatch(map(listOf(other, reordered), walls = listOf(flippedWall)), listOf(rect, other), emptyList(), listOf(wall)))
    }

    @Test
    fun one_pixel_of_drift_is_tolerated_but_not_more() {
        val nudged = rect.copy(x0 = rect.x0 + 50, x3 = rect.x3 + 50)
        assertTrue(sameZones(listOf(rect), listOf(nudged)))
        val moved = rect.copy(x0 = rect.x0 + 51, x3 = rect.x3 + 51)
        assertFalse(sameZones(listOf(rect), listOf(moved)))
    }

    @Test
    fun a_missing_or_extra_shape_fails() {
        assertFalse(restrictionsMatch(map(listOf(rect)), listOf(rect, other), emptyList(), emptyList()))
        assertFalse(restrictionsMatch(map(listOf(rect, other)), listOf(rect), emptyList(), emptyList()))
        assertFalse(restrictionsMatch(map(walls = listOf(wall)), emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun zone_kinds_are_checked_separately() {
        val asNoMop = rect.copy(kind = ZoneKind.NO_MOP)
        assertFalse(restrictionsMatch(map(noMop = listOf(asNoMop)), listOf(rect), emptyList(), emptyList()))
        assertTrue(restrictionsMatch(map(noMop = listOf(asNoMop)), emptyList(), listOf(asNoMop), emptyList()))
    }

    @Test
    fun duplicates_must_each_be_present() {
        assertFalse(sameZones(listOf(rect, rect), listOf(rect, other)))
        assertTrue(sameZones(listOf(rect, rect), listOf(rect, rect)))
    }
}
