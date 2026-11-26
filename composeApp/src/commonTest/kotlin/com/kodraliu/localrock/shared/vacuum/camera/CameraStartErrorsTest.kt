package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CameraStartErrorsTest {
    @Test
    fun every_robot_code_maps_to_the_official_error_and_text() {
        val expected = mapOf(
            null to -100, -10009 to -105, -10010 to -106, -10011 to -101, -10012 to -102,
            -10013 to -104, -10014 to -108, -10015 to -103, -10016 to -107, -10021 to -116, -99999 to -103,
        )
        expected.forEach { (robot, app) -> assertEquals(app, CameraStartErrors.fromRobotCode(robot).appCode, "robot $robot") }
        assertEquals("Please move the device away from the charging dock to view the video.", CameraStartErrors.fromRobotCode(-10011).message)
        assertEquals("Unable to remote viewing. Emptying in progress.", CameraStartErrors.fromRobotCode(-10021).message)
        assertEquals("Please reset the robot and try again.", CameraStartErrors.fromRobotCode(-10010).message)
        assertTrue(CameraStartErrors.fromRobotCode(-10013).display().endsWith("(error -104)"))
    }

    @Test
    fun only_transient_errors_are_retryable() {
        listOf(null, -10015, -10016, -12345).forEach {
            assertTrue(CameraStartErrors.fromRobotCode(it).retryable, "robot $it")
        }
        listOf(-10009, -10010, -10011, -10012, -10013, -10014, -10021).forEach {
            assertEquals(false, CameraStartErrors.fromRobotCode(it).retryable, "robot $it")
        }
    }

    @Test
    fun a_transient_error_is_retried_500ms_apart_until_it_works() = runTest {
        val pauses = mutableListOf<Long>()
        var calls = 0
        val result = CameraStartRetry.run({ pauses += it }) {
            calls++
            if (calls < 4) CameraStartRetry.Attempt.Refused(null) else CameraStartRetry.Attempt.Ok
        }
        assertNull(result)
        assertEquals(4, calls)
        assertEquals(listOf(500L, 500L, 500L), pauses)
    }

    @Test
    fun a_permanent_error_stops_at_once() = runTest {
        var calls = 0
        val result = CameraStartRetry.run({ error("no pause expected") }) {
            calls++
            CameraStartRetry.Attempt.Refused(-10011)
        }
        assertEquals(-101, result?.appCode)
        assertEquals(1, calls)
    }

    @Test
    fun eight_retries_then_minus_117() = runTest {
        var calls = 0
        var pauses = 0
        val result = CameraStartRetry.run({ pauses++ }) {
            calls++
            CameraStartRetry.Attempt.Refused(-10015)
        }
        assertEquals(-117, result?.appCode)
        assertEquals("The maximum number of retries has been reached.", result?.message)
        assertEquals(9, calls)
        assertEquals(8, pauses)
    }
}
