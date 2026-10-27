package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LiveMapViewportTest {
    private val map = ParsedMap(100, 100, ByteArray(10000), emptyList(), 0.05f)
    private val size = Size(400f, 600f)
    private val fit = 2f to Offset(10f, 20f)

    @Test fun refreshedPixelsAndRobotPathKeepUserViewport() {
        val state = LiveMapViewport()
        val scope = "robot-a" to 1
        state.frameIfNeeded(map.viewportIdentity(scope), size, fit)
        state.scale = 5f
        state.offset = Offset(-300f, -200f)
        val refreshed = map.copy(grid = ByteArray(10000) { 128.toByte() },
            robotMm = ParsedMapPoint(100, 200), pathMm = listOf(ParsedMapPoint(50, 100)))
        state.frameIfNeeded(refreshed.viewportIdentity(scope), size, 1.5f to Offset.Zero)
        assertEquals(5f, state.scale)
        assertEquals(Offset(-300f, -200f), state.offset)
    }

    @Test fun switchingRobotOrFloorReframesEvenWithIdenticalGeometry() {
        val state = LiveMapViewport()
        state.frameIfNeeded(map.viewportIdentity("a" to 1), size, fit)
        state.scale = 6f
        state.frameIfNeeded(map.viewportIdentity("b" to 1), size, fit)
        assertEquals(2f, state.scale)
        state.scale = 6f
        state.frameIfNeeded(map.viewportIdentity("b" to 2), size, fit)
        assertEquals(2f, state.scale)
    }

    @Test fun resizeKeepsZoomButCoordinateSpaceChangeReframes() {
        val state = LiveMapViewport()
        val key = map.viewportIdentity("a" to 1)
        state.frameIfNeeded(key, size, fit)
        state.scale = 6f
        state.frameIfNeeded(key, Size(600f, 400f), 3f to Offset.Zero)
        assertEquals(6f, state.scale)
        assertEquals(Offset(110f, -80f), state.offset)
        val shifted = map.copy(pixelOffsetLeft = 10).viewportIdentity("a" to 1)
        assertNotEquals(key, shifted)
        state.frameIfNeeded(shifted, Size(600f, 400f), fit)
        assertEquals(fit.second, state.offset)
        assertEquals(2f, state.scale)
    }

    @Test fun resizeKeepsTheMapPointAtTheViewCentre() {
        val state = LiveMapViewport()
        val key = map.viewportIdentity("a" to 1)
        state.frameIfNeeded(key, size, fit)
        state.scale = 5f
        state.offset = Offset(-300f, -200f)
        state.frameIfNeeded(key, Size(600f, 800f), 1.5f to Offset.Zero)
        assertEquals(5f, state.scale)
        assertEquals(Offset(-200f, -100f), state.offset)
    }

    @Test fun waitsForMeasurementAndDoubleTapUsesLatestFit() {
        val state = LiveMapViewport()
        val key = map.viewportIdentity(null)
        state.frameIfNeeded(key, Size.Zero, null)
        state.frameIfNeeded(key, size, fit)
        assertEquals(2f, state.scale)
        state.scale = 7f
        val latestFit = 3f to Offset(30f, 40f)
        state.frameIfNeeded(key, size, latestFit)
        assertEquals(7f, state.scale)
        state.reset(latestFit)
        assertEquals(3f, state.scale)
        assertEquals(latestFit.second, state.offset)
    }
}
