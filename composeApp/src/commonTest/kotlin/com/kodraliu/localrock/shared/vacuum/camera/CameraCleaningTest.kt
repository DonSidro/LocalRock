package com.kodraliu.localrock.shared.vacuum.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CameraCleaningTest {
    @Test fun playStartsCleaningFromIdleAndBothDockedStates() {
        for (state in listOf(2, 3, 8, 24, 100)) {
            assertEquals(CameraCleaningAction.START, cameraCleaningAction(state, 0, 0))
            assertEquals("app_start", cameraCleaningCommand(CameraCleaningAction.START, state, 0, 0))
        }
    }
    @Test fun resumePreservesWholeHomeRoomAndZoneTasks() {
        for ((scope, method) in mapOf(1 to "app_start", 2 to "resume_zoned_clean", 3 to "resume_segment_clean")) {
            assertEquals(CameraCleaningAction.RESUME, cameraCleaningAction(10, scope, 0))
            assertEquals(method, cameraCleaningCommand(CameraCleaningAction.RESUME, 10, scope, 0))
        }
    }
    @Test fun runningCleaningUsesPauseAndStalePlayCannotToggleIt() {
        for (state in listOf(5, 11, 17, 18)) {
            assertEquals("app_pause", cameraCleaningCommand(CameraCleaningAction.PAUSE, state, 1, 0))
            assertNull(cameraCleaningCommand(CameraCleaningAction.START, state, 1, 0))
        }
        assertNull(cameraCleaningCommand(CameraCleaningAction.PAUSE, 3, 0, 0))
    }
    @Test fun unknownErrorNavigationMappingAndMaintenanceStatesAreUnavailable() {
        for (state in listOf(null, 4, 6, 7, 9, 12, 14, 15, 16, 22, 23, 26, 29)) {
            assertNull(cameraCleaningAction(state, 0, 0), "state=$state")
        }
        assertNull(cameraCleaningAction(8, null, 0))
        assertNull(cameraCleaningAction(10, 99, 0))
        assertNull(cameraCleaningAction(3, 0, null))
        assertNull(cameraCleaningAction(3, 0, 1))
    }
}
