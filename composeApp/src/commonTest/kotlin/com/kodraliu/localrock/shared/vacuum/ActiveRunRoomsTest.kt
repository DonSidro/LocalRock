package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.map.LegacyMapRevision
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveRunRoomsTest {
    private val cleaning = VacuumStatus(state = VacuumStateCodes.SEGMENT_CLEANING, inCleaning = 3, cleanTime = 10, mapStatus = 7)
    private fun map(ids: Set<Int>? = setOf(1, 3), sequence: Long = 1) = ParsedMap(
        3, 1, byteArrayOf(128.toByte(), 128.toByte(), 128.toByte()),
        listOf(ParsedMapRoom(1, "One"), ParsedMapRoom(2, "Two"), ParsedMapRoom(3, "Three")), 0.05f,
        reportedCleanSegments = ids, legacyRevision = LegacyMapRevision(1, sequence),
        robotMm = ParsedMapPoint(50, 0), // room 2, which is not a target
        originalGrid = byteArrayOf(15, 23, 31),
    )
    private fun ActiveRunRooms.receive(map: ParsedMap?) = withMap(map, generation)

    @Test
    fun oem_started_room_run_uses_all_reported_targets_not_the_robot_room() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        assertEquals(setOf(1, 3), run.roomIds)
        assertEquals(setOf(1, 3), run.receive(map().copy(robotMm = ParsedMapPoint(100, 0))).roomIds)
    }

    @Test
    fun explicit_full_home_scope_uses_all_map_rooms() {
        val run = ActiveRunRooms().withStatus(cleaning.copy(inCleaning = 1, state = VacuumStateCodes.CLEANING))
        assertEquals(setOf(1, 2, 3), run.receive(map(ids = null)).roomIds)
    }

    @Test
    fun unknown_scope_zone_and_spot_runs_do_not_guess_rooms() {
        listOf(
            VacuumStatus(state = VacuumStateCodes.CLEANING),
            cleaning.copy(inCleaning = 2, state = VacuumStateCodes.ZONED_CLEANING),
            VacuumStatus(state = VacuumStateCodes.SPOT_CLEANING),
            VacuumStatus(state = VacuumStateCodes.SPOT_CLEANING, inCleaning = 1),
            VacuumStatus(state = VacuumStateCodes.MAPPING, inCleaning = 1),
            cleaning.copy(inCleaning = 4),
        ).forEach { status -> assertNull(ActiveRunRooms().withStatus(status).receive(map()).roomIds) }
    }

    @Test
    fun segment_state_can_identify_scope_on_older_firmware() {
        val run = ActiveRunRooms().withStatus(VacuumStatus(state = VacuumStateCodes.SEGMENT_CLEANING))
        assertEquals(setOf(1, 3), run.receive(map()).roomIds)
    }

    @Test
    fun absent_empty_or_invalid_target_data_clears_previous_targets() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        listOf(map(ids = null), map(ids = emptySet()), map(ids = setOf(1, 9)), null).forEach {
            assertNull(run.receive(it).roomIds)
        }
    }

    @Test
    fun paused_run_keeps_its_targets_when_the_robot_reports_the_same_task() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        assertEquals(setOf(1, 3), run.withStatus(cleaning.copy(state = VacuumStateCodes.PAUSED)).roomIds)
    }

    @Test
    fun completing_a_run_clears_targets_even_if_the_map_still_contains_block_11() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        val finished = run.withStatus(cleaning.copy(state = VacuumStateCodes.CHARGING, inCleaning = 0))
        assertNull(finished.roomIds)
        assertNull(finished.receive(map()).roomIds)
    }

    @Test
    fun a_new_run_waits_for_a_new_map_and_rejects_late_previous_run_responses() {
        val old = ActiveRunRooms().withStatus(cleaning).receive(map())
        val stopped = old.withStatus(cleaning.copy(inCleaning = 0))
        val next = stopped.withStatus(cleaning.copy(cleanTime = 0))
        assertNull(next.roomIds)
        assertNull(next.withMap(map(), old.generation).roomIds)
        assertNull(next.receive(map()).roomIds) // same header revision as the previous run
        assertEquals(setOf(3), next.receive(map(setOf(3), sequence = 2)).roomIds)
    }

    @Test
    fun missed_idle_transition_is_detected_by_a_reset_cleaning_clock() {
        val old = ActiveRunRooms().withStatus(cleaning).receive(map())
        val next = old.withStatus(cleaning.copy(cleanTime = 1))
        assertNull(next.roomIds)
        assertNull(next.withMap(map(), old.generation).roomIds)
    }

    @Test
    fun floor_changes_and_failed_refreshes_invalidate_in_flight_maps() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        val otherFloor = run.withStatus(cleaning.copy(mapStatus = 11))
        assertNull(otherFloor.roomIds)
        assertNull(otherFloor.withMap(map(), run.generation).roomIds)
        val failed = run.invalidate()
        assertNull(failed.roomIds)
        assertNull(failed.withMap(map(), run.generation).roomIds)
        assertNull(failed.receive(map()).roomIds)
        assertEquals(setOf(1, 3), failed.withStatus(cleaning).receive(map()).roomIds)
    }

    @Test
    fun partial_pushes_never_reestablish_run_scope_after_failure() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        assertEquals(setOf(1, 3), run.withStatePush(null).roomIds)
        val failed = run.invalidate()
        assertNull(failed.withStatePush(null).receive(map()).roomIds)
        assertNull(failed.withStatePush(cleaning.state).receive(map()).roomIds)
        assertNull(run.withStatePush(VacuumStateCodes.CHARGING).roomIds)
        assertNull(run.withStatePush(VacuumStateCodes.CLEANING).withMap(map(), run.generation).roomIds)
    }

    @Test
    fun a_fresh_status_without_scope_does_not_reuse_the_previous_scope() {
        val run = ActiveRunRooms().withStatus(cleaning).receive(map())
        assertNull(run.withStatus(VacuumStatus(state = VacuumStateCodes.CLEANING)).receive(map()).roomIds)
    }
}
