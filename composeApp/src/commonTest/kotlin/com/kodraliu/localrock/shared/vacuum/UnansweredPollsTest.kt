package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnansweredPollsTest {
    private fun polls() = UnansweredPolls(3)

    @Test
    fun becomesTrueAfterExactlyThreeFailures() {
        val p = polls()
        p.pollFailed(); p.pollFailed()
        assertFalse(p.unresponsive.value)
        p.pollFailed()
        assertTrue(p.unresponsive.value)
    }

    @Test
    fun aReplyClearsItAndRestartsTheCount() {
        val p = polls()
        repeat(3) { p.pollFailed() }
        p.heardFromRobot()
        assertFalse(p.unresponsive.value)
        p.pollFailed(); p.pollFailed()
        assertFalse(p.unresponsive.value)
    }

    @Test
    fun aPushThenOneFailedPollDoesNotFlipItBack() {
        val p = polls()
        repeat(3) { p.pollFailed() }
        p.heardFromRobot()
        p.pollFailed()
        assertFalse(p.unresponsive.value)
    }

    @Test
    fun failuresBeforeAReplyDoNotCarryOver() {
        val p = polls()
        p.pollFailed(); p.pollFailed()
        p.heardFromRobot()
        p.pollFailed()
        assertFalse(p.unresponsive.value)
    }
}
