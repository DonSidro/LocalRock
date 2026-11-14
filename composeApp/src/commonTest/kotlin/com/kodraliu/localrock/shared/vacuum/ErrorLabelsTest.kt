package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorLabelsTest {
    @Test
    fun codesThatWereWrongNowMatchTheOfficialTable() {
        assertEquals("Camera error", VacuumErrorCodes.describe(25))
        assertEquals("Jammed mop module", VacuumErrorCodes.describe(27))
        assertEquals("Roller mop not installed", VacuumErrorCodes.describe(76))
    }

    @Test
    fun internalAndDockRangesGetOneLabel() {
        assertEquals("Internal error. Reset the robot.", VacuumErrorCodes.describe(104))
        assertTrue(VacuumErrorCodes.describe(126).startsWith("Dock error"))
    }

    @Test
    fun unknownCodesStillShowTheNumber() {
        assertEquals("Error code 300", VacuumErrorCodes.describe(300))
        assertEquals("Dock error 300", DockErrorCodes.describe(300))
    }

    @Test
    fun dockLabelsFallBackToTheOfficialTable() {
        assertEquals("Drain error", DockErrorCodes.describe(49))
        assertEquals("Dock dust duct blocked", DockErrorCodes.describe(34))
    }
}
