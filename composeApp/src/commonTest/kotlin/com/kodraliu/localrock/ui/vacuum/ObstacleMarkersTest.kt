package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObstacleMarkersTest {

    // 100 x 100 cells of 50 mm from (0, 0), drawn 1:1, so cell (10, 89) is 500 mm right and
    // 500 mm up from the bottom-left corner.
    private val shoe = MapObstacle(x = 500, y = 500, type = 2, confidence = 0.9f, photoId = "a", ignored = false)
    private val cable = MapObstacle(x = 1500, y = 500, type = 0, confidence = 0.8f, photoId = null, ignored = false)
    private val map = ParsedMap(
        width = 100, height = 100, grid = ByteArray(100 * 100), rooms = emptyList(), resolution = 0.05f,
        format = MapFormat.LEGACY, obstacles = listOf(shoe, cable),
    )
    private fun view(k: Float = 1f, pan: Offset = Offset.Zero, yaw: Float = 0f, squash: Float = 1f) =
        MapView(canvasW = 100f, canvasH = 100f, centerX = 50f, centerY = 50f, k = k, pan = pan, yawDegrees = yaw, squash = squash)

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun an_obstacle_sits_where_the_renderer_puts_its_cell() {
        assertNear(Offset(10f, 89f), map.obstacleOnScreen(shoe, view(), 1f))
        // Zoomed 2x about the centre and panned by (5, 5).
        assertNear(Offset(-25f, 133f), map.obstacleOnScreen(shoe, view(k = 2f, pan = Offset(5f, 5f)), 1f))
    }

    @Test
    fun an_obstacle_follows_the_map_when_it_is_turned() {
        // A quarter turn about the centre carries the cell at (10, 89) to (11, 10).
        assertNear(Offset(11f, 10f), map.obstacleOnScreen(shoe, view(yaw = 90f), 1f))
    }

    @Test
    fun a_tap_opens_the_nearest_obstacle_within_reach() {
        assertEquals(shoe, map.obstacleAt(Offset(12f, 90f), view(), 1f, reachPx = 5f))
        assertEquals(cable, map.obstacleAt(Offset(29f, 89f), view(), 1f, reachPx = 5f))
        assertNull(map.obstacleAt(Offset(50f, 50f), view(), 1f, reachPx = 5f))
    }
}
