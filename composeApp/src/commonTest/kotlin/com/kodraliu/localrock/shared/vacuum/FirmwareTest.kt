package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FirmwareTest {
    @Test
    fun readsTheRobotsOtaAnswers() {
        assertEquals("downloading", parseOtaState(Json.parseToJsonElement("""{"ota_state":"downloading"}""")))
        assertEquals("idle", parseOtaState(Json.parseToJsonElement("""[{"ota_state":"idle"}]""")))
        assertEquals(42, parseOtaProgress(Json.parseToJsonElement("""{"ota_progress":42}""")))
        assertNull(parseOtaState(null))
    }

    @Test
    fun aRobotAsleepOnItsChargerMayUpdateButOneOffTheDockMayNot() {
        assertNull(firmwareInstallBlocker(state = 2, battery = 100, chargeStatus = 1, inCleaning = 0, errorCode = 0))
        assertNull(firmwareInstallBlocker(state = 8, battery = 90, chargeStatus = 1, inCleaning = 0, errorCode = 0))
        assertNotNull(firmwareInstallBlocker(state = 2, battery = 100, chargeStatus = 0, inCleaning = 0, errorCode = 0))
        assertNotNull(firmwareInstallBlocker(state = 5, battery = 100, chargeStatus = 0, inCleaning = 1, errorCode = 0))
    }

    @Test
    fun theBatteryAndErrorsBlockToo() {
        assertTrue("80%" in firmwareInstallBlocker(8, 70, 1, 0, 0)!!)
        assertNotNull(firmwareInstallBlocker(8, 100, 1, 0, 12))
        assertNotNull(firmwareInstallBlocker(null, null, null, null, null))
    }

    @Test
    fun theProgressFollowsDownloadInstallRebootAndFinish() {
        var step = nextFirmwareStep(FirmwareProgress.Starting, false, true, "idle", null)
        assertEquals(FirmwareProgress.Starting, step.progress) // still idle at the very start: not finished
        assertTrue(!step.finished)

        step = nextFirmwareStep(step.progress, step.seenWorking, true, "downloading", 40)
        assertEquals(FirmwareProgress.Downloading(40), step.progress)

        step = nextFirmwareStep(step.progress, step.seenWorking, true, "installing", 0)
        assertEquals(FirmwareProgress.Installing, step.progress)

        step = nextFirmwareStep(step.progress, step.seenWorking, false, null, null)
        assertEquals(FirmwareProgress.Restarting, step.progress)

        step = nextFirmwareStep(step.progress, step.seenWorking, true, "idle", 100)
        assertTrue(step.finished && step.progress !is FirmwareProgress.Failed)
    }

    @Test
    fun silenceBeforeTheRobotStartedIsNotARestartAndAFailureEnds() {
        assertEquals(FirmwareProgress.Starting, nextFirmwareStep(FirmwareProgress.Starting, false, false, null, null).progress)
        val failed = nextFirmwareStep(FirmwareProgress.Installing, true, true, "failed", null)
        assertTrue(failed.finished && failed.progress is FirmwareProgress.Failed)
    }
}
