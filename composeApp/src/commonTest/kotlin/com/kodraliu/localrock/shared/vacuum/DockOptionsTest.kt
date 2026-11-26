package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class DockOptionsTest {
    private val highTempDocks = listOf(29, 31, 40, 41, 42, 43, 46)

    @Test
    fun high_temperature_docks_offer_room_and_high_temperature() {
        for (dock in highTempDocks) {
            assertEquals(listOf(0 to "Room Temperature", 2 to "High Temperature"),
                DockOptions.washWaterTemperature(dock).map { it.code to it.label })
        }
    }

    @Test
    fun other_docks_offer_room_warm_and_hot_water() {
        for (dock in listOf(null, 0, 17, 30)) {
            assertEquals(listOf(0 to "Room Temperature", 1 to "Warm Water", 2 to "Hot Water"),
                DockOptions.washWaterTemperature(dock).map { it.code to it.label })
        }
    }

    @Test
    fun a_high_temperature_dock_reads_warm_as_high() {
        assertEquals(2, DockOptions.washWaterTemperatureCode(1, 29))
        assertEquals("High Temperature", DockOptions.washWaterTemperature(29).labelFor(DockOptions.washWaterTemperatureCode(1, 29)!!))
        assertEquals(0, DockOptions.washWaterTemperatureCode(0, 29))
        assertEquals(1, DockOptions.washWaterTemperatureCode(1, 17))
        assertEquals(null, DockOptions.washWaterTemperatureCode(null, 29))
    }

    @Test
    fun a_code_with_no_name_is_still_shown() {
        assertEquals("Other (9)", DockOptions.washWaterTemperature(17).labelFor(9))
    }

    @Test
    fun codes_in_each_list_are_unique() {
        for (list in listOf(DockOptions.washWaterTemperature(29), DockOptions.washWaterTemperature(17), DockOptions.dryTime)) {
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
