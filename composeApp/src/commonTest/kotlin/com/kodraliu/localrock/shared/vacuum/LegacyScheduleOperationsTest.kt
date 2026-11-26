package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.device.DeviceApiException
import com.kodraliu.localrock.shared.schedule.ScheduleException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import com.kodraliu.localrock.shared.schedule.ServerSchedule
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LegacyScheduleOperationsTest {
    @Test fun unavailable_jobs_fall_back_for_404_and_405() = runTest {
        for (status in listOf(404, 405)) {
            var legacyReads = 0
            val result = loadScheduleList(
                readJobs = { throw DeviceApiException(status, "unavailable") },
                readLegacy = { legacyReads++; listOf("legacy") },
            )
            assertEquals(listOf("legacy"), result)
            assertEquals(1, legacyReads)
        }
    }

    @Test fun server_failure_does_not_fall_back() = runTest {
        var legacyReads = 0
        val failure = DeviceApiException(500, "server failure")
        val actual = assertFailsWith<DeviceApiException> {
            loadScheduleList({ throw failure }, { legacyReads++; listOf("legacy") })
        }
        assertSame(failure, actual)
        assertEquals(0, legacyReads)
    }

    @Test fun empty_jobs_are_authoritative() = runTest {
        var legacyReads = 0
        assertEquals(emptyList(), loadScheduleList({ emptyList<String>() }, { legacyReads++; listOf("legacy") }))
        assertEquals(0, legacyReads)
    }

    @Test fun refused_legacy_replacement_restores_original() = runTest {
        val old = CleanTimer("old", false, 7, 30, setOf(1, 3), settings = TimerSettings(fanPower = 102))
        var stored: CleanTimer? = null // The delete succeeded.
        var restores = 0
        val error = assertFailsWith<ScheduleException> {
            replaceLegacySchedule({ error("replacement refused") }, { restores++; stored = old })
        }
        assertEquals(old, stored)
        assertEquals(1, restores)
        assertTrue(error.message.orEmpty().contains("old schedule was restored"))
    }

    @Test fun failed_restoration_reports_both_failures() = runTest {
        val error = assertFailsWith<ScheduleException> {
            replaceLegacySchedule({ error("replacement refused") }, { error("restore refused") })
        }
        assertTrue(error.message.orEmpty().contains("replacement refused"))
        assertTrue(error.message.orEmpty().contains("restore refused"))
    }

    @Test fun successful_replacement_does_not_restore() = runTest {
        var restored = false
        replaceLegacySchedule({}, { restored = true })
        assertEquals(false, restored)
    }

    @Test fun cancellation_does_not_start_restoration() = runTest {
        var restored = false
        assertFailsWith<CancellationException> {
            replaceLegacySchedule({ throw CancellationException("cancelled") }, { restored = true })
        }
        assertEquals(false, restored)
    }

    private fun job(id: String, nativeName: String?) = CleanTimer(
        id, true, 7, 0, emptySet(),
        serverJob = ServerSchedule(buildJsonObject {
            put("id", id)
            put("param", buildJsonObject {
                put("method", "server_scheduled_start")
                put("params", buildJsonArray { add(buildJsonObject { nativeName?.let { put("name", it) } }) })
            })
        }),
    )

    @Test fun robot_only_timers_are_added_after_server_jobs() {
        val jobs = job("55", null)
        val robotOnly = CleanTimer("200", true, 9, 30, setOf(1))
        val merged = mergeRobotOnlyTimers(listOf(jobs), listOf(robotOnly))
        assertEquals(listOf(jobs, robotOnly), merged)
        assertEquals(null, merged.last().serverJob)
    }

    @Test fun mirrored_timer_is_shown_once_by_its_native_name() {
        val mirrored = job("55", "1700000000")
        val robot = CleanTimer("1700000000", true, 7, 0, emptySet())
        assertEquals(listOf(mirrored), mergeRobotOnlyTimers(listOf(mirrored), listOf(robot)))
    }

    @Test fun job_id_alone_also_matches() {
        val same = job("100", null)
        assertEquals(listOf(same), mergeRobotOnlyTimers(listOf(same), listOf(CleanTimer("100", false, 7, 0, emptySet()))))
    }

    @Test fun unrelated_robot_timer_is_kept() {
        val mirrored = job("55", "1700000000")
        val other = CleanTimer("1700000999", true, 8, 0, emptySet())
        assertEquals(listOf(mirrored, other), mergeRobotOnlyTimers(listOf(mirrored), listOf(other)))
    }

    @Test fun failed_robot_read_keeps_the_previous_robot_only_timers() = runTest {
        val jobs = listOf(job("55", "1700000000"))
        val kept = CleanTimer("200", true, 9, 30, setOf(1))
        val previous = listOf(job("55", "1700000000"), kept)
        val merged = listWithRobotTimers(jobs, previous) { throw IllegalStateException("offline") }
        assertEquals(listOf(jobs[0], kept), merged)
        assertEquals(jobs, listWithRobotTimers(jobs, emptyList()) { throw IllegalStateException("offline") })
    }

    @Test fun successful_robot_read_drops_a_deleted_robot_timer() = runTest {
        val previous = listOf(CleanTimer("200", true, 9, 30, setOf(1)))
        assertEquals(emptyList(), listWithRobotTimers(emptyList(), previous) { emptyList() })
    }

    @Test fun cancellation_is_not_swallowed_by_the_robot_read() = runTest {
        assertFailsWith<CancellationException> { listWithRobotTimers(emptyList(), emptyList()) { throw CancellationException("cancelled") } }
    }
}
