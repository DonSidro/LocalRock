package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.ZoneRect
import kotlin.test.Test
import kotlin.test.assertEquals

class ZoneEditTest {

    private val zone = ZoneRect(x1 = 1000, y1 = 2000, x2 = 3000, y2 = 5000)

    @Test
    fun moving_keeps_the_size() {
        assertEquals(ZoneRect(1500, 1800, 3500, 4800), zone.movedBy(500, -200))
    }

    @Test
    fun dragging_a_corner_keeps_the_opposite_corner() {
        // Corner 2 is (x2, y2); its opposite (x1, y1) stays.
        assertEquals(ZoneRect(1000, 2000, 4000, 6000), zone.withCorner(2, 4000, 6000))
        // Corner 0 is (x1, y1); its opposite (x2, y2) stays.
        assertEquals(ZoneRect(500, 1000, 3000, 5000), zone.withCorner(0, 500, 1000))
    }

    @Test
    fun a_corner_dragged_past_the_opposite_one_flips_the_zone() {
        // Corner 2 dragged to the left of x1 and below y1: the zone now lies on the other side of (1000, 2000).
        assertEquals(ZoneRect(0, 500, 1000, 2000), zone.withCorner(2, 0, 500))
    }

    @Test
    fun a_zone_never_gets_smaller_than_the_minimum_side() {
        // Corner 1 (x2, y1) dragged almost onto the fixed corner (x1, y2) = (1000, 5000).
        assertEquals(ZoneRect(1000, 4500, 1500, 5000), zone.withCorner(1, 1100, 4900))
    }

    @Test
    fun the_repeat_count_survives_an_edit() {
        val twice = zone.copy(times = 2)
        assertEquals(2, twice.movedBy(10, 10).times)
        assertEquals(2, twice.withCorner(3, 0, 9000).times)
    }
}
