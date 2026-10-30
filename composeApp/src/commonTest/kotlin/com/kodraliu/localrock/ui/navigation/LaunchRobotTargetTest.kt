package com.kodraliu.localrock.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchRobotTargetTest {

    @Test
    fun single_paired_robot_is_opened() {
        assertEquals("a", launchRobotTarget(listOf("a"), null))
        assertEquals("a", launchRobotTarget(listOf("a"), "stale-other"))
    }

    @Test
    fun last_opened_robot_is_opened_when_still_paired() {
        assertEquals("b", launchRobotTarget(listOf("a", "b"), "b"))
    }

    @Test
    fun no_guess_among_several_without_a_last_opened() {
        assertNull(launchRobotTarget(listOf("a", "b"), null))
    }

    @Test
    fun stale_last_opened_stays_on_the_list() {
        assertNull(launchRobotTarget(listOf("a", "b"), "c"))
    }

    @Test
    fun no_robots_stays_on_the_list() {
        assertNull(launchRobotTarget(emptyList(), "a"))
        assertNull(launchRobotTarget(emptyList(), null))
    }
}
