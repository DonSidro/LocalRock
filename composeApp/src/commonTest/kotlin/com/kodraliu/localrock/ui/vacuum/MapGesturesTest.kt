package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapGesturesTest {
    @Test
    fun two_fingers_dragged_together_up_or_down_tilt_the_camera() {
        assertTrue(isTiltDrag(2, Offset(0.5f, -9f), zoom = 1.002f, rotation = 0.3f))
        assertTrue(isTiltDrag(2, Offset(-1f, 7f), zoom = 1f, rotation = 0f))
    }

    @Test
    fun a_pinch_whose_midpoint_drifts_does_not_tilt() {
        assertFalse(isTiltDrag(2, Offset(0f, -9f), zoom = 1.05f, rotation = 0f))
        assertFalse(isTiltDrag(2, Offset(0f, -9f), zoom = 0.94f, rotation = 0f))
    }

    @Test
    fun a_twist_or_a_sideways_drag_or_one_finger_does_not_tilt() {
        assertFalse(isTiltDrag(2, Offset(0f, -9f), zoom = 1f, rotation = 4f))
        assertFalse(isTiltDrag(2, Offset(8f, -9f), zoom = 1f, rotation = 0f))
        assertFalse(isTiltDrag(1, Offset(0f, -9f), zoom = 1f, rotation = 0f))
    }
}
