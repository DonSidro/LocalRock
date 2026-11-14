package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapViewTest {

    private fun view(yaw: Float, squash: Float, k: Float = 2f, pan: Offset = Offset(15f, -9f)) =
        MapView(
            canvasW = 400f, canvasH = 600f,
            centerX = 120f, centerY = 90f,
            k = k, pan = pan, yawDegrees = yaw, squash = squash,
        )

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun a_tap_lands_back_on_the_map_point_that_was_drawn_there_at_any_heading_and_tilt() {
        // The same maths places the floor, the labels and the tap, so this is what keeps a room's
        // label on its room and a tap on the room under the finger.
        for (yaw in listOf(0f, 35f, 90f, 137f, 180f, 270f, 359f)) {
            for (squash in listOf(1f, 0.6f)) {
                val v = view(yaw, squash)
                val screen = v.toScreen(200f, 40f)
                val back = v.toBitmap(screen.x, screen.y)
                assertNear(200f, back.x)
                assertNear(40f, back.y)
            }
        }
    }

    @Test
    fun the_plan_turns_about_its_own_centre_so_the_centre_never_moves_when_the_heading_does() {
        val before = view(0f, 0.6f).toScreen(120f, 90f)
        val after = view(123f, 0.6f).toScreen(120f, 90f)

        assertNear(before.x, after.x)
        assertNear(before.y, after.y)
    }

    @Test
    fun a_quarter_turn_moves_a_point_to_where_a_quarter_turn_should_put_it() {
        // 10 map units right of the centre, flat view, 1 screen px per unit: a clockwise quarter
        // turn swings it to straight below the centre.
        val flat = MapView(100f, 100f, 0f, 0f, k = 1f, pan = Offset.Zero, yawDegrees = 90f, squash = 1f)

        val at = flat.toScreen(10f, 0f)

        assertNear(50f, at.x)
        assertNear(60f, at.y)
    }

    @Test
    fun height_lifts_a_point_up_the_screen_but_not_in_the_flat_view() {
        val tilted = view(35f, 0.6f)
        val flat = view(35f, 1f)

        assertTrue(tilted.toScreen(50f, 50f, height = 7f).y < tilted.toScreen(50f, 50f).y)
        assertEquals(flat.toScreen(50f, 50f).y, flat.toScreen(50f, 50f, height = 7f).y)
    }

    @Test
    fun tilting_the_camera_squashes_the_floor_and_lifts_walls_by_the_matching_amount() {
        // Straight down: floor undistorted, no wall height visible. Tipped 60°: floor squashed to half
        // and a wall's height shows at the matching sin(60°) of its length.
        assertNear(1f, squashForTilt(0f))
        assertNear(0.5f, squashForTilt(60f))

        val tipped = view(0f, squashForTilt(60f))
        assertNear(0.866f, tipped.lift, tolerance = 0.001f)
        assertNear(0f, view(0f, squashForTilt(0f)).lift)
    }

    @Test
    fun a_tap_still_lands_on_the_point_drawn_there_at_every_tilt() {
        for (tilt in listOf(0f, 20f, 53f, MAP_3D_MAX_TILT)) {
            val v = view(70f, squashForTilt(tilt))
            val screen = v.toScreen(80f, 150f)
            val back = v.toBitmap(screen.x, screen.y)
            assertNear(80f, back.x, tolerance = 0.05f)
            assertNear(150f, back.y, tolerance = 0.05f)
        }
    }

    @Test
    fun the_free_turning_fit_does_not_change_with_the_heading() {
        val a = fitScale(400f, 600f, 300f, 200f, yawDegrees = 0f, squash = 0.6f, freeTurning = true)
        val b = fitScale(400f, 600f, 300f, 200f, yawDegrees = 71f, squash = 0.6f, freeTurning = true)

        assertEquals(a, b)
    }

    @Test
    fun the_flat_fit_follows_the_heading_so_a_quarter_turn_fits_the_turned_plan() {
        // A 300x100 plan on a 400x600 canvas: upright it is limited by width (400/300). Turned a
        // quarter it is 100 wide and 300 tall, so the canvas height limits it instead (600/300).
        val upright = fitScale(400f, 600f, 300f, 100f, yawDegrees = 0f, squash = 1f, freeTurning = false)
        val turned = fitScale(400f, 600f, 300f, 100f, yawDegrees = 90f, squash = 1f, freeTurning = false)

        assertNear(400f / 300f, upright)
        assertNear(2f, turned)
    }

    @Test
    fun a_layer_is_drawn_where_the_label_and_the_tap_think_it_is_at_any_heading_and_tilt() {
        // The floor, the walls and the route go through layerMatrix; labels, obstacles and taps through
        // toScreen. They must be the same transform, or the names drift off the rooms once the model turns.
        for (yaw in listOf(0f, 35f, 90f, 137f, 270f)) {
            for (squash in listOf(1f, 0.6f, 0.4f)) {
                val v = view(yaw, squash)
                for (height in listOf(0f, 12f)) {
                    val viaLayer = v.layerMatrix(height).map(Offset(200f, 40f))
                    val viaScreen = v.toScreen(200f, 40f, height)
                    assertNear(viaScreen.x, viaLayer.x)
                    assertNear(viaScreen.y, viaLayer.y)
                }
            }
        }
    }
}
