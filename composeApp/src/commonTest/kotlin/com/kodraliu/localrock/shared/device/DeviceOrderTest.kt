package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceOrderTest {

    private fun device(name: String, duid: String = name) = Device(duid = duid, name = name)

    @Test
    fun robots_paired_in_any_order_are_listed_by_name() {
        // The order from #133: the second downstairs robot was paired last.
        val paired = listOf(device("Downstairs Entryway"), device("Upstairs"), device("Downstairs Main"))
        assertEquals(
            listOf("Downstairs Entryway", "Downstairs Main", "Upstairs"),
            paired.sortedForList().map { it.name },
        )
    }

    @Test
    fun case_and_surrounding_spaces_do_not_change_the_order() {
        val paired = listOf(device("kitchen"), device(" Bedroom"), device("Attic"))
        assertEquals(listOf("Attic", " Bedroom", "kitchen"), paired.sortedForList().map { it.name })
    }

    @Test
    fun numbers_in_names_compare_as_numbers() {
        val paired = listOf(device("Robot 10"), device("Robot 2"), device("Robot 1"))
        assertEquals(listOf("Robot 1", "Robot 2", "Robot 10"), paired.sortedForList().map { it.name })
        assertTrue(naturalCompare("Robot 02", "Robot 10") < 0)
    }

    @Test
    fun equal_names_keep_a_stable_order_by_device_id() {
        val paired = listOf(device("S8", duid = "b"), device("S8", duid = "a"))
        assertEquals(listOf("a", "b"), paired.sortedForList().map { it.duid })
    }
}
