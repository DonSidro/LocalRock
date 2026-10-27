package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.device.DeviceApiException
import com.kodraliu.localrock.shared.schedule.ScheduleException
import kotlinx.coroutines.CancellationException
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
}
