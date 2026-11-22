package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class DryTimeOptionsTest {
    private fun hours(model: String?, current: Int? = null) =
        DockCapabilities(model, null, null).dryTimeOptions(current).map { it.label }

    @Test
    fun the_qrevo_curv_gets_3_4_and_5_hours() {
        assertEquals(listOf("3 h", "4 h", "5 h"), hours("roborock.vacuum.a245"))
    }

    @Test
    fun other_models_get_2_3_and_4_hours() {
        assertEquals(listOf("2 h", "3 h", "4 h"), hours("roborock.vacuum.a101"))
        assertEquals(listOf("2 h", "3 h", "4 h"), hours(null))
    }

    @Test
    fun a_value_the_robot_reports_outside_the_list_is_kept() {
        assertEquals(listOf("2 h", "3 h", "4 h", "1 h"), hours("roborock.vacuum.a101", 3600))
    }

    @Test
    fun five_hours_is_18000_seconds() {
        assertEquals(18000, DockCapabilities("roborock.vacuum.a245", null, null).dryTimeOptions(null).last().code)
    }
}
