package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class EmptyWaterTankCommandTest {

    @Test
    fun the_command_is_the_one_the_official_app_sends() {
        // Captured from the official app on a Qrevo Pro (firmware 02.15.26): after the confirm prompt it
        // sent `app_empty_inbuilt_water_tank` with params {}, and the robot answered ["ok"].
        // python-roborock has app_empty_rinse_tank_water, which is a different command.
        assertEquals("app_empty_inbuilt_water_tank", APP_EMPTY_INBUILT_WATER_TANK)
    }

    @Test
    fun the_params_are_an_empty_object_not_an_empty_list() {
        assertEquals("{}", EMPTY_INBUILT_WATER_TANK_PARAMS.toString())
    }
}
