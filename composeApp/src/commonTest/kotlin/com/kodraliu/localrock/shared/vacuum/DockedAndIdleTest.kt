package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockedAndIdleTest {
    @Test
    fun chargingOnTheDockWithNoCleanIsDockedAndIdle() {
        assertTrue(VacuumStatus(state = VacuumStateCodes.CHARGING, inCleaning = 0).isDockedAndIdle())
        assertTrue(VacuumStatus(state = VacuumStateCodes.CHARGING_COMPLETE, inCleaning = 0).isDockedAndIdle())
    }

    @Test
    fun aRunningCleanIsNotIdleEvenWhenTheStateLooksDocked() {
        assertFalse(VacuumStatus(state = VacuumStateCodes.CHARGING, inCleaning = 1).isDockedAndIdle())
    }

    @Test
    fun aRobotAwayFromTheDockOrWithNoStatusIsNotDockedAndIdle() {
        assertFalse(VacuumStatus(state = VacuumStateCodes.IDLE, inCleaning = 0).isDockedAndIdle())
        assertFalse(VacuumStatus().isDockedAndIdle())
    }
}
