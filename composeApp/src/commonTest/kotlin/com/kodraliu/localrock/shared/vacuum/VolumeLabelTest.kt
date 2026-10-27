package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class VolumeLabelTest {
    @Test
    fun only_zero_is_off() {
        // A Qrevo Curv 2 Flow reports [10] and an S8 Pro Ultra [20]; both still speak.
        assertEquals("Off", volumeLabel(0))
        assertEquals("Low (10%)", volumeLabel(10))
        assertEquals("Low (20%)", volumeLabel(20))
        assertEquals(1, volumeLevelIndex(10))
    }

    @Test
    fun the_picker_levels_show_their_name_alone() {
        for ((index, level) in VolumeLevels.withIndex()) {
            assertEquals(level.second, volumeLabel(level.first))
            assertEquals(index, volumeLevelIndex(level.first))
        }
    }

    @Test
    fun values_between_levels_take_the_nearest_audible_one() {
        assertEquals("Medium (50%)", volumeLabel(50))
        assertEquals("High (90%)", volumeLabel(90))
        assertEquals(3, volumeLevelIndex(90))
    }
}
