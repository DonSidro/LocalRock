package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LiveMapViewportTest {
    private val map = ParsedMap(100, 100, ByteArray(10000), emptyList(), 0.05f)

    private fun assertNear(expected: Offset, actual: Offset, tolerance: Float = 0.01f) =
        assertTrue(
            abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
            "expected $expected but was $actual",
        )

    @Test fun refreshedPixelsAndRobotPathKeepUserViewport() {
        val state = LiveMapViewport()
        val scope = "robot-a" to 1
        state.frameIfNeeded(map.viewportIdentity(scope) to false)
        state.zoom = 5f
        state.pan = Offset(-3f, -2f)
        val refreshed = map.copy(grid = ByteArray(10000) { 128.toByte() },
            robotMm = ParsedMapPoint(100, 200), pathMm = listOf(ParsedMapPoint(50, 100)))
        state.frameIfNeeded(refreshed.viewportIdentity(scope) to false)
        assertEquals(5f, state.zoom)
        assertEquals(Offset(-3f, -2f), state.pan)
    }

    @Test fun switchingRobotFloorOrViewModeReframes() {
        val state = LiveMapViewport()
        state.frameIfNeeded(map.viewportIdentity("a" to 1) to false)
        state.zoom = 6f
        state.frameIfNeeded(map.viewportIdentity("b" to 1) to false)
        assertEquals(1f, state.zoom)
        state.zoom = 6f
        state.frameIfNeeded(map.viewportIdentity("b" to 2) to false)
        assertEquals(1f, state.zoom)
        state.zoom = 6f
        state.frameIfNeeded(map.viewportIdentity("b" to 2) to true)
        assertEquals(1f, state.zoom)
    }

    @Test fun aChangeOfCoordinateSpaceReframes() {
        val state = LiveMapViewport()
        val key = map.viewportIdentity("a" to 1)
        state.frameIfNeeded(key to false)
        state.zoom = 4f
        state.pan = Offset(2f, 2f)
        val shifted = map.copy(pixelOffsetLeft = 10).viewportIdentity("a" to 1)
        assertNotEquals(key, shifted)
        state.frameIfNeeded(shifted to false)
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.pan)
    }

    @Test fun resetReturnsToTheFramedView() {
        val state = LiveMapViewport()
        state.zoom = 3f
        state.pan = Offset(1f, 1f)
        state.reset()
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.pan)
    }

    // The window changes size on a fold or unfold, which changes how the plan is framed (the fit).
    private fun view(canvasW: Float, canvasH: Float, fit: Float, zoom: Float, pan: Offset) =
        framedView(canvasW, canvasH, centerX = 120f, centerY = 90f, fit = fit, zoom = zoom, pan = pan, yawDegrees = 35f, squash = 0.7f)

    @Test fun resizeKeepsTheZoomAndTheMapPointAtTheViewCentre() {
        val pan = Offset(-3f, 2f)
        val narrow = view(400f, 600f, fit = 2f, zoom = 5f, pan = pan)
        val wide = view(900f, 600f, fit = 3.2f, zoom = 5f, pan = pan)
        assertNear(narrow.toBitmap(200f, 300f), wide.toBitmap(450f, 300f))
        assertEquals(5f, wide.k / 3.2f)
    }

    @Test fun aViewTheUserHasNotMovedIsFramedAfreshOnResize() {
        // Zoom 1 and no pan: the plan's centre sits at the canvas centre whatever the size.
        for ((w, h, fit) in listOf(Triple(400f, 600f, 2f), Triple(900f, 600f, 3.2f))) {
            assertNear(Offset(w / 2f, h / 2f), view(w, h, fit, zoom = 1f, pan = Offset.Zero).toScreen(120f, 90f))
        }
    }
}
