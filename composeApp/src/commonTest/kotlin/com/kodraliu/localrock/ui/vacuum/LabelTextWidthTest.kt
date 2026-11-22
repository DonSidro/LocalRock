package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class LabelTextWidthTest {
    @Test
    fun an_unknown_room_width_gets_the_whole_view() {
        assertEquals(300, labelTextWidth(spanKnown = false, roomPx = 0f, minPx = 100, viewPx = 300))
    }

    @Test
    fun a_known_width_is_floored_and_capped() {
        assertEquals(100, labelTextWidth(true, 20f, 100, 300))
        assertEquals(180, labelTextWidth(true, 180f, 100, 300))
        assertEquals(300, labelTextWidth(true, 900f, 100, 300))
    }
}
