package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.model.SceneRunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoutineRunMessageTest {

    @Test
    fun a_started_routine_says_so() {
        assertEquals("\"Kitchen first\" started", routineRunMessage("Kitchen first", SceneRunResult(status = "started")))
    }

    @Test
    fun running_the_active_routine_again_stops_it() {
        assertEquals("Stopping \"Kitchen first\"", routineRunMessage("Kitchen first", SceneRunResult(status = "cancel_requested")))
    }

    @Test
    fun another_running_routine_is_named_in_the_error() {
        val e = assertFailsWith<IllegalStateException> {
            routineRunMessage(
                "Kitchen first",
                SceneRunResult(accepted = false, status = "routine_in_progress", activeSceneName = "Whole home"),
            )
        }
        assertEquals("\"Whole home\" is still running. Run it again to stop it first.", e.message)
    }
}
