package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DockedRobotTest {
    private val charger = ParsedMapPoint(26306, 25598, -177)
    private val stale = ParsedMapPoint(24343, 38108, 170)
    private val map = ParsedMap(width = 1, height = 1, grid = ByteArray(1), rooms = emptyList(), resolution = 1f, chargerMm = charger, robotMm = stale)

    @Test
    fun charging_and_dock_activities_count_as_docked() {
        assertTrue(isDocked(8))
        assertTrue(isDocked(22))
        assertTrue(isDocked(23))
        assertTrue(isDocked(27))
        assertTrue(isDocked(100))
    }

    @Test
    fun cleaning_idle_and_unknown_do_not() {
        assertFalse(isDocked(5))
        assertFalse(isDocked(3))
        assertFalse(isDocked(null))
    }

    @Test
    fun a_docked_robot_is_drawn_at_the_charger() {
        assertEquals(charger, map.withRobotOnDock(true).robotMm)
    }

    @Test
    fun an_undocked_robot_keeps_the_position_in_the_map() {
        assertSame(map, map.withRobotOnDock(false))
    }

    @Test
    fun a_map_without_a_charger_is_left_alone() {
        val noCharger = map.copy(chargerMm = null)
        assertSame(noCharger, noCharger.withRobotOnDock(true))
    }
}
