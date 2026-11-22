package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.device.firmwareStartRefusal
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
        assertTrue("20%" in firmwareInstallBlocker(8, 19, 1, 0, 0)!!)
        assertNull(firmwareInstallBlocker(8, 20, 1, 0, 0))
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

        repeat(FIRMWARE_IDLE_POLLS - 1) {
            step = nextFirmwareStep(step.progress, step.seenWorking, true, "idle", 100, step.idleStreak)
            assertTrue(!step.finished)
        }
        step = nextFirmwareStep(step.progress, step.seenWorking, true, "idle", 100, step.idleStreak)
        assertTrue(step.finished && step.progress !is FirmwareProgress.Failed)
    }

    // Each entry is one poll: the robot's OTA state, or null when it did not answer.
    private fun watch(vararg polls: String?): FirmwareStep {
        var step = FirmwareStep(FirmwareProgress.Starting, false)
        for (state in polls) {
            step = nextFirmwareStep(step.progress, step.seenWorking, state != null, state, null, step.idleStreak)
            if (step.finished) break
        }
        return step
    }

    @Test
    fun anIdleBetweenDownloadAndInstallDoesNotEndTheWatch() {
        assertTrue(!watch("downloading", "idle", "installing").finished)
        assertEquals(FirmwareProgress.Installing, watch("downloading", "idle", "installing").progress)
        assertTrue(!watch("downloading", "idle", "idle", "installing", "idle").finished)
    }

    @Test
    fun theWatchEndsAfterIdleHoldsThroughTheRebootGap() {
        assertTrue(!watch("installing", null, "idle", null, "idle").finished)
        assertTrue(watch("installing", null, "idle", "idle", "idle").finished)
        assertTrue(watch("downloading", "installing", null, null, "idle", "idle", "idle").finished)
    }

    @Test
    fun aFailedStateEndsTheWatchAtOnce() {
        val step = watch("downloading", "failed")
        assertTrue(step.finished && step.progress is FirmwareProgress.Failed)
    }

    @Test
    fun aChangedVersionIsDoneAndAnUnchangedOneIsAFailure() {
        assertEquals(FirmwareProgress.Done("02.35.52"), firmwareOutcome("02.33.48", "02.35.52", "02.35.52"))
        val rolledBack = firmwareOutcome("02.33.48", "02.35.52", "02.33.48")
        assertTrue(rolledBack is FirmwareProgress.Failed && "rolled back or did not apply" in rolledBack.reason)
        assertTrue(firmwareOutcome("02.33.48", "02.35.52", "02.34.00") is FirmwareProgress.Failed)
    }

    @Test
    fun withoutAKnownTargetOnlyAChangeFromTheOldVersionCounts() {
        assertEquals(FirmwareProgress.Done("02.35.52"), firmwareOutcome("02.33.48", null, "02.35.52"))
        assertTrue(firmwareOutcome("02.33.48", null, "02.33.48") is FirmwareProgress.Failed)
        assertTrue(firmwareOutcome(null, null, "02.35.52") is FirmwareProgress.Failed)
    }

    @Test
    fun anOlderTargetIsDoneWhenTheRobotReportsIt() {
        assertEquals(FirmwareProgress.Done("02.33.48"), firmwareOutcome("02.35.52", "02.33.48", "02.33.48"))
    }

    @Test
    fun anUnreadableVersionIsNotDone() {
        assertTrue(firmwareOutcome("02.33.48", "02.35.52", null) is FirmwareProgress.Failed)
    }

    @Test
    fun aServerWithoutInstallSupportIsExplainedAndOtherRefusalsKeepTheirCode() {
        assertTrue("enable_install" in firmwareStartRefusal(404))
        assertTrue("enable_install" in firmwareStartRefusal(501))
        assertTrue("HTTP 500" in firmwareStartRefusal(500))
    }

    @Test
    fun aRobotAlreadyDownloadingOrInstallingWhenThePageOpensIsPickedUp() {
        val downloading = nextFirmwareStep(FirmwareProgress.Idle, false, true, "downloading", 55)
        assertEquals(FirmwareProgress.Downloading(55), downloading.progress)
        assertTrue(downloading.seenWorking)
        assertTrue(nextFirmwareStep(FirmwareProgress.Idle, false, true, "installing", 0).seenWorking)
        assertTrue(!nextFirmwareStep(FirmwareProgress.Idle, false, true, "idle", null).seenWorking)
        assertTrue(!nextFirmwareStep(FirmwareProgress.Idle, false, false, null, null).seenWorking)
    }

    @Test
    fun silenceBeforeTheRobotStartedIsNotARestartAndAFailureEnds() {
        assertEquals(FirmwareProgress.Starting, nextFirmwareStep(FirmwareProgress.Starting, false, false, null, null).progress)
        val failed = nextFirmwareStep(FirmwareProgress.Installing, true, true, "failed", null)
        assertTrue(failed.finished && failed.progress is FirmwareProgress.Failed)
    }
}
