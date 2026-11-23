package com.kodraliu.localrock.shared.notify

import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class RobotEventsTest {

    private val docked = VacuumStatus(state = VacuumStateCodes.CHARGING, errorCode = 0, dockErrorStatus = 0, inCleaning = 0)
    private val cleaning = docked.copy(state = VacuumStateCodes.CLEANING, inCleaning = 1)

    private fun events(vararg statuses: VacuumStatus): List<RobotEvent> {
        val detector = RobotEventDetector()
        return statuses.flatMap { detector.update(it) }
    }

    @Test
    fun what_is_already_wrong_when_watching_starts_is_not_announced() {
        assertEquals(emptyList(), events(docked.copy(errorCode = 8, dockErrorStatus = DockErrorCodes.WATER_EMPTY)))
    }

    @Test
    fun a_new_error_is_announced_once() {
        val stuck = cleaning.copy(state = VacuumStateCodes.ERROR, errorCode = 8)
        assertEquals(listOf(RobotEvent.Error(8)), events(cleaning, stuck, stuck))
    }

    @Test
    fun an_error_that_changes_or_comes_back_is_announced_again() {
        val stuck = cleaning.copy(errorCode = 8)
        val brush = cleaning.copy(errorCode = 5)
        assertEquals(
            listOf(RobotEvent.Error(8), RobotEvent.Error(5), RobotEvent.Error(8)),
            events(cleaning, stuck, brush, cleaning, stuck),
        )
    }

    @Test
    fun an_error_pushed_before_any_status_is_known_is_not_announced() {
        assertEquals(emptyList(), events(VacuumStatus(errorCode = 8)))
    }

    @Test
    fun dock_problems_are_announced_when_they_appear() {
        val empty = docked.copy(dockErrorStatus = DockErrorCodes.WATER_EMPTY)
        val full = docked.copy(dockErrorStatus = DockErrorCodes.DIRTY_TANK_FULL)
        assertEquals(
            listOf(RobotEvent.DockAlert(DockErrorCodes.WATER_EMPTY), RobotEvent.DockAlert(DockErrorCodes.DIRTY_TANK_FULL)),
            events(docked, empty, empty, full),
        )
    }

    @Test
    fun a_run_that_ends_on_the_dock_is_finished() {
        val returning = cleaning.copy(state = VacuumStateCodes.RETURNING_HOME)
        val washing = cleaning.copy(state = VacuumStateCodes.WASHING_MOP)
        val done = docked.copy(cleanArea = 42_460_000L, cleanTime = 3_290L)
        assertEquals(
            listOf(RobotEvent.CleanFinished(42_460_000L, 3_290L)),
            events(docked, cleaning, returning, washing, done, done),
        )
    }

    @Test
    fun recharging_partway_through_a_job_is_not_the_end_of_it() {
        val recharging = cleaning.copy(state = VacuumStateCodes.CHARGING)
        assertEquals(emptyList(), events(cleaning, recharging, cleaning))
        assertEquals(listOf(RobotEvent.CleanFinished(null, null)), events(cleaning, recharging, cleaning, docked))
    }

    @Test
    fun pausing_or_stopping_away_from_the_dock_is_not_finished() {
        assertEquals(
            emptyList(),
            events(cleaning, cleaning.copy(state = VacuumStateCodes.PAUSED), cleaning.copy(state = VacuumStateCodes.IDLE, inCleaning = 0)),
        )
    }

    @Test
    fun a_robot_that_does_not_report_in_cleaning_is_finished_once_it_charges() {
        val bare = VacuumStatus(state = VacuumStateCodes.CLEANING, errorCode = 0)
        assertEquals(
            listOf(RobotEvent.CleanFinished(null, null)),
            events(bare, bare.copy(state = VacuumStateCodes.CHARGING)),
        )
    }

    @Test
    fun a_robot_carried_back_to_the_dock_with_an_error_is_not_finished() {
        assertEquals(
            listOf(RobotEvent.Error(8)),
            events(cleaning, cleaning.copy(errorCode = 8), docked.copy(errorCode = 8)),
        )
    }

    @Test
    fun a_run_is_in_progress_from_the_first_cleaning_state_until_it_ends() {
        val detector = RobotEventDetector()
        fun inProgressAfter(status: VacuumStatus): Boolean {
            detector.update(status)
            return detector.runInProgress
        }
        assertEquals(false, inProgressAfter(docked))
        assertEquals(true, inProgressAfter(cleaning))
        assertEquals(true, inProgressAfter(cleaning.copy(state = VacuumStateCodes.WASHING_MOP)))
        assertEquals(true, inProgressAfter(cleaning.copy(state = VacuumStateCodes.CHARGING)))
        assertEquals(true, inProgressAfter(cleaning.copy(state = VacuumStateCodes.RETURNING_HOME, inCleaning = 0)))
        assertEquals(false, inProgressAfter(docked))
    }

    @Test
    fun a_run_stopped_out_on_the_floor_is_over_without_being_finished() {
        val detector = RobotEventDetector()
        detector.update(cleaning)
        assertEquals(emptyList(), detector.update(cleaning.copy(state = VacuumStateCodes.IDLE, inCleaning = 0)))
        assertEquals(false, detector.runInProgress)
    }

    @Test
    fun progress_text() {
        val status = cleaning.copy(
            state = VacuumStateCodes.SEGMENT_CLEANING,
            cleanPercent = 42,
            cleanArea = 12_460_000L,
            cleanTime = 1_090L,
            battery = 64,
        )
        assertEquals(
            RobotProgress("d1", "S8: Room cleaning in Kitchen", "42% · 12.5 m² · 18 min · Battery 64%", 42),
            progressOf("d1", "S8", status, room = "Kitchen"),
        )
        // The room is where it is cleaning, so it is left out once it is doing something else.
        assertEquals(
            "S8: Returning home",
            progressOf("d1", "S8", status.copy(state = VacuumStateCodes.RETURNING_HOME), room = "Kitchen").title,
        )
        assertEquals("S8: The robot is trapped or stuck", progressOf("d1", "S8", status.copy(errorCode = 8), room = "Kitchen").title)
        assertEquals(
            RobotProgress("d1", "S8: Cleaning", "", null),
            progressOf("d1", "S8", VacuumStatus(state = VacuumStateCodes.CLEANING), room = null),
        )
    }

    @Test
    fun notification_text() {
        assertEquals(
            RobotNotification("d1", "S8", "The robot is trapped or stuck", alert = true),
            RobotEvent.Error(8).toNotification("d1", "S8"),
        )
        assertEquals(
            RobotNotification("d1", "S8", "Clean water tank empty", alert = true),
            RobotEvent.DockAlert(DockErrorCodes.WATER_EMPTY).toNotification("d1", "S8"),
        )
        assertEquals(
            RobotNotification("d1", "S8", "Cleaning finished: 42.5 m² in 55 min", alert = false),
            RobotEvent.CleanFinished(42_460_000L, 3_290L).toNotification("d1", "S8"),
        )
        assertEquals("Cleaning finished", RobotEvent.CleanFinished(null, 0).toNotification("d1", "S8").body)
    }
}
