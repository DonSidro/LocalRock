package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class DockOptionsTest {
    @Test
    fun the_confirmed_water_temperature_code_has_its_name() {
        assertEquals("Hot water", DockOptions.washWaterTemperature.labelFor(2))
    }

    @Test
    fun a_code_with_no_name_is_still_shown() {
        assertEquals("Other (9)", DockOptions.washWaterTemperature.labelFor(9))
    }

    @Test
    fun codes_in_each_list_are_unique() {
        for (list in listOf(DockOptions.washWaterTemperature, DockOptions.dryTime)) {
            assertEquals(list.size, list.map { it.code }.toSet().size)
        }
    }

    @Test
    fun drying_time_reads_in_hours_or_minutes() {
        assertEquals("2 h", dryTimeLabel(7200))
        assertEquals("90 min", dryTimeLabel(5400))
        assertEquals("45 s", dryTimeLabel(45))
        assertEquals(7200, DockOptions.dryTime.first { it.label == "2 h" }.code)
    }
}
