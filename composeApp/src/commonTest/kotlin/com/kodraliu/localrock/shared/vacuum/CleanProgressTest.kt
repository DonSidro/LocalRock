package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanProgressTest {

    private val docked = VacuumStatus(state = VacuumStateCodes.CHARGING, inCleaning = 0)
    private val cleaning = VacuumStatus(state = VacuumStateCodes.CLEANING, inCleaning = 1)
    private val recharging = cleaning.copy(state = VacuumStateCodes.CHARGING)
    private val washing = cleaning.copy(state = VacuumStateCodes.WASHING_MOP)

    @Test
    fun a_robot_reporting_a_percent_maps_to_a_clamped_percent() {
        assertEquals(
            CleanProgress(42, true, CleanProgress.Phase.Cleaning),
            cleaning.copy(cleanPercent = 42).toCleanProgress(),
        )
    }

    @Test
    fun a_percent_out_of_range_is_clamped() {
        assertEquals(0, cleaning.copy(cleanPercent = -3).toCleanProgress().percent)
        assertEquals(100, cleaning.copy(cleanPercent = 150).toCleanProgress().percent)
    }

    @Test
    fun a_legacy_robot_that_never_reports_the_field_gets_a_null_percent() {
        val progress = cleaning.copy(cleanPercent = null).toCleanProgress()
        assertTrue(progress.jobActive)
        assertNull(progress.percent)
    }

    @Test
    fun in_cleaning_zero_is_the_end_even_while_in_a_cleaning_state() {
        val progress = cleaning.copy(inCleaning = 0).toCleanProgress()
        assertFalse(progress.jobActive)
        assertEquals(CleanProgress.Phase.Over, progress.phase)
        assertNull(progress.percent)
    }

    @Test
    fun a_robot_that_does_not_report_in_cleaning_is_taken_at_its_word() {
        val progress = cleaning.copy(inCleaning = null).toCleanProgress()
        assertTrue(progress.jobActive)
        assertEquals(CleanProgress.Phase.Cleaning, progress.phase)
    }

    @Test
    fun a_mid_job_break_keeps_the_job_active_but_freezes_the_phase() {
        val progress = recharging.copy(cleanPercent = 61).toCleanProgress()
        assertTrue(progress.jobActive)
        assertEquals(CleanProgress.Phase.Break, progress.phase)
        assertEquals(61, progress.percent)
    }

    @Test
    fun a_paused_job_is_active_but_paused() {
        val paused = cleaning.copy(state = VacuumStateCodes.PAUSED)
        assertEquals(
            CleanProgress(null, true, CleanProgress.Phase.Paused),
            paused.toCleanProgress(),
        )
    }

    @Test
    fun a_docked_robot_has_no_job() {
        assertEquals(
            CleanProgress(null, false, CleanProgress.Phase.Over),
            docked.copy(cleanPercent = 100).toCleanProgress(),
        )
    }

    @Test
    fun a_break_then_end_lifecycle_does_not_come_back_active() {
        val sequence = listOf(
            cleaning.copy(cleanPercent = 20),
            washing.copy(cleanPercent = 55),
            recharging.copy(cleanPercent = 55),
            docked.copy(cleanPercent = 55),
        )
        val phases = sequence.map { it.toCleanProgress().phase }
        assertEquals(
            listOf(
                CleanProgress.Phase.Cleaning,
                CleanProgress.Phase.Break,
                CleanProgress.Phase.Break,
                CleanProgress.Phase.Over,
            ),
            phases,
        )
    }
}
