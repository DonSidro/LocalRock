package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathPlaybackTest {
    @Test
    fun speedsCycleOneThreeFive() {
        assertEquals(3, nextPlaybackSpeed(1))
        assertEquals(5, nextPlaybackSpeed(3))
        assertEquals(1, nextPlaybackSpeed(5))
    }

    @Test
    fun stepIsHundredMillisOverSpeed() {
        assertEquals(100f, playbackStepMillis(1))
        assertEquals(20f, playbackStepMillis(5))
        assertEquals(100f, playbackStepMillis(0)) // clamped like setSpeed
        assertEquals(10f, playbackStepMillis(20))
    }

    @Test
    fun advanceStopsAtTheLastPoint() {
        assertEquals(1.5f, advancePlayback(1f, 50, 1, lastIndex = 10))
        assertEquals(3.5f, advancePlayback(1f, 50, 5, lastIndex = 10))
        assertNull(advancePlayback(9.5f, 50, 1, lastIndex = 10))
    }

    @Test
    fun playPauseStop() {
        val p = PathPlayback()
        p.position = 4f
        p.play()
        assertEquals(PlaybackStatus.PLAYING, p.status)
        assertEquals(0f, p.position) // a new replay starts at the beginning
        p.position = 2f
        p.pause()
        assertEquals(PlaybackStatus.PAUSED, p.status)
        p.play()
        assertEquals(2f, p.position) // resume keeps its place
        p.stop()
        assertFalse(p.active)
    }

    private fun map(points: Int) = ParsedMap(
        width = 2, height = 2, grid = ByteArray(4), rooms = emptyList(), resolution = 0.05f,
        pathMm = List(points) { ParsedMapPoint(it * 50, 0) },
        robotMm = ParsedMapPoint(0, 0),
    )

    @Test
    fun replayNeedsARoute() {
        assertFalse(canPlayBack(null))
        assertFalse(canPlayBack(map(1)))
        assertTrue(canPlayBack(map(2)))
        val bare = map(3).withoutRoute()
        assertTrue(bare.pathMm.isEmpty())
        assertNull(bare.robotMm)
    }

    @Test
    fun headingFollowsTheRouteAndHoldsThroughPauses() {
        val path = listOf(ParsedMapPoint(0, 0), ParsedMapPoint(100, 0), ParsedMapPoint(100, 0), ParsedMapPoint(100, 100))
        assertEquals(0f, playbackHeadingDegrees(path, 0))
        assertEquals(0f, playbackHeadingDegrees(path, 1))
        assertEquals(90f, playbackHeadingDegrees(path, 2))
        assertNull(playbackHeadingDegrees(listOf(ParsedMapPoint(5, 5), ParsedMapPoint(5, 5)), 0))
    }

    @Test
    fun stillStartTakesTheFirstMove() {
        val path = listOf(ParsedMapPoint(0, 0), ParsedMapPoint(0, 0), ParsedMapPoint(0, -50))
        assertEquals(-90f, playbackHeadingDegrees(path, 0))
    }

    @Test
    fun playbackRobotInterpolatesAndFacesTravel() {
        val map = ParsedMap(
            width = 1, height = 1, grid = ByteArray(1), rooms = emptyList(), resolution = 0.05f,
            pathMm = listOf(ParsedMapPoint(0, 0), ParsedMapPoint(100, 0)),
        )
        val robot = map.playbackRobot(0.5f)
        assertEquals(50, robot.x)
        assertEquals(0, robot.y)
        assertEquals(-180, robot.angle)
        assertEquals(-270, map.playbackRobot(0.5f, yawDegrees = 90f).angle)
    }
}
