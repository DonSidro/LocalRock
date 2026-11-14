package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StateLabelsTest {
    @Test
    fun theCodesTheOfficialAppNamesNeverShowAsStateN() {
        val named = listOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 22, 23, 25, 26, 28, 29, 30, 32, 33, 34, 36, 37, 38, 39, 40, 41, 42, 43, 100, 101, 103)
        for (code in named) {
            assertFalse(VacuumStateCodes.describe(code).startsWith("State "), "state $code has no label")
        }
    }

    @Test
    fun codesTwoSourcesAgreeOnUseTheirNames() {
        assertEquals("In call", VacuumStateCodes.describe(28))
        assertEquals("Remote controlling", VacuumStateCodes.describe(7))
        assertEquals("On cruise", VacuumStateCodes.describe(32))
        assertEquals("Washing mop", VacuumStateCodes.describe(23))
        assertEquals(VacuumStateCodes.describe(23), VacuumStateCodes.describe(25))
        assertEquals("Reinstalling mop cloth mounts", VacuumStateCodes.describe(33))
        assertEquals("Detaching mop cloth mounts", VacuumStateCodes.describe(34))
        assertEquals("Offline", VacuumStateCodes.describe(101))
    }

    @Test
    fun codesNoSourceNamesStayAsTheyAre() {
        assertEquals("State 31", VacuumStateCodes.describe(31))
        assertEquals("State 60", VacuumStateCodes.describe(60))
    }
}
