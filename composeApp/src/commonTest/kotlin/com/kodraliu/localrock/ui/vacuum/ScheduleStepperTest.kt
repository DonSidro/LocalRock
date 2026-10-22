package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleStepperTest {
    @Test
    fun minutes_step_up_through_the_quarter_hours_and_wrap_to_zero() {
        val seen = generateSequence(0) { steppedWithWrap(it, 15, 0, 59) }.take(5).toList()

        assertEquals(listOf(0, 15, 30, 45, 0), seen)
    }

    @Test
    fun minutes_step_down_through_the_quarter_hours_and_wrap_to_45() {
        val seen = generateSequence(0) { steppedWithWrap(it, -15, 0, 59) }.take(5).toList()

        assertEquals(listOf(0, 45, 30, 15, 0), seen)
    }

    @Test
    fun minutes_step_one_at_a_time_and_wrap_at_both_ends() {
        assertEquals(listOf(57, 58, 59, 0, 1), generateSequence(57) { steppedWithWrap(it, 1, 0, 59) }.take(5).toList())
        assertEquals(listOf(2, 1, 0, 59, 58), generateSequence(2) { steppedWithWrap(it, -1, 0, 59) }.take(5).toList())
    }

    @Test
    fun a_24_hour_value_maps_to_the_12_hour_face_and_back() {
        assertEquals(12, hour12Of(0))
        assertEquals(12, hour12Of(12))
        assertEquals(1, hour12Of(13))
        assertEquals(11, hour12Of(23))
        // Every hour survives the round trip, so the AM/PM button never changes the hour by accident.
        for (hour in 0..23) assertEquals(hour, hour24Of(hour12Of(hour), pm = hour >= 12))
    }

    @Test
    fun times_are_written_in_the_devices_clock_style() {
        assertEquals("17:05", formatClock(17, 5, use24 = true))
        assertEquals("05:05 PM", formatClock(17, 5, use24 = false))
        assertEquals("12:00 AM", formatClock(0, 0, use24 = false))
        assertEquals("12:30 PM", formatClock(12, 30, use24 = false))
        assertEquals("00:00", formatClock(0, 0, use24 = true))
    }

    @Test
    fun hours_wrap_between_23_and_0() {
        assertEquals(0, steppedWithWrap(23, 1, 0, 23))
        assertEquals(23, steppedWithWrap(0, -1, 0, 23))
    }
}
