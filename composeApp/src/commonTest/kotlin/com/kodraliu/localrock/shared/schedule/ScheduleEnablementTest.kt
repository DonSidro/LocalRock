package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ScheduleEnablementTest {
    private class Fixture(serverEnabled: Boolean = false, native: Boolean = true, robotEnabled: Boolean = false) {
        var job: ServerSchedule? = ServerSchedule(buildJsonObject {
            put("id", 918265); put("cron", "10 9 ? * 0"); put("enabled", serverEnabled)
            put("timeZoneId", "America/Los_Angeles"); put("repeated", true)
            put("param", buildJsonObject {
                put("method", "server_scheduled_start")
                put("params", JsonArray(listOf(buildJsonObject {
                    if (native) put("name", "1680623937705")
                    put("map_index", 1); put("segments", "20,17"); put("fan_power", 102)
                    put("future", buildJsonObject { put("keep", true) })
                })))
            })
        })
        var extraJobs = emptyList<ServerSchedule>()
        var robot = robotEnabled
        var reads = 0
        var timerReads = 0
        var timerWrites = 0
        var jobWrites = 0
        val events = mutableListOf<String>()
        var beforeRead: (Int) -> Unit = {}
        var timerResponse: (() -> JsonElement?)? = null
        var failTimerReadAt: Int? = null
        var failJobReadAt: Int? = null
        var applyTimer = true
        var failTimerWrite = false
        var applyJob = true
        var failJobWrite = false
        var gate: CompletableDeferred<Unit>? = null
        val sync = ScheduleEnablement(
            readJobs = {
                reads++; beforeRead(reads)
                events += "readJob"
                if (failJobReadAt == reads) error("connection failed")
                listOfNotNull(job) + extraJobs
            },
            writeJob = { _, patch ->
                jobWrites++; events += "writeJob"
                if (applyJob) job = ServerSchedule(JsonObject(job!!.raw + patch))
                if (failJobWrite) error("HTTP response lost")
            },
            readTimers = {
                timerReads++; events += "readTimer"
                gate?.await()
                if (failTimerReadAt == timerReads) error("MQTT unavailable")
                timerResponse?.invoke() ?: JsonArray(listOf(JsonArray(listOf(
                    JsonPrimitive("1680623937705"), JsonPrimitive(if (robot) "on" else "off"), JsonObject(emptyMap())
                ))))
            },
            writeTimer = { name, enabled ->
                assertEquals("1680623937705", name)
                timerWrites++; events += "writeTimer"
                if (applyTimer) robot = enabled
                if (failTimerWrite) error("MQTT reply lost")
            },
        )
        suspend fun set(enabled: Boolean, expected: ServerSchedule = job!!): ServerSchedule =
            sync.save(expected, buildJsonObject { put("enabled", enabled) }, true)
    }

    @Test fun both_directions_verify_robot_before_http_and_preserve_payload() = runTest {
        for (desired in listOf(true, false)) {
            val f = Fixture(serverEnabled = !desired, robotEnabled = !desired)
            val before = f.job!!.raw
            val result = f.set(desired)
            assertEquals(desired, f.robot)
            assertEquals(desired, result.enabled)
            assertEquals(before - "enabled", result.raw - "enabled")
            assertEquals(1, f.timerWrites); assertEquals(1, f.jobWrites)
            assertEquals(listOf("readJob", "readTimer", "readJob", "writeTimer", "readTimer", "readJob", "writeJob", "readJob"), f.events)
        }
    }

    @Test fun mismatched_states_only_write_the_side_that_needs_it() = runTest {
        for (desired in listOf(true, false)) {
            val robotMismatch = Fixture(serverEnabled = desired, robotEnabled = !desired)
            robotMismatch.set(desired)
            assertEquals(1, robotMismatch.timerWrites); assertEquals(0, robotMismatch.jobWrites)
            val serverMismatch = Fixture(serverEnabled = !desired, robotEnabled = desired)
            serverMismatch.set(desired)
            assertEquals(0, serverMismatch.timerWrites); assertEquals(1, serverMismatch.jobWrites)
            val matching = Fixture(serverEnabled = desired, robotEnabled = desired)
            matching.set(desired)
            assertEquals(0, matching.timerWrites); assertEquals(0, matching.jobWrites)
        }
    }

    @Test fun server_only_server_scheduled_start_jobs_never_use_mqtt() = runTest {
        val f = Fixture(native = false)
        f.set(true); f.set(false)
        assertEquals(0, f.timerReads); assertEquals(0, f.timerWrites)
        assertEquals(2, f.jobWrites)
        assertNull(f.job!!.nativeName)
    }

    @Test fun missing_ambiguous_and_unknown_timers_are_not_recreated() = runTest {
        val row = JsonArray(listOf(JsonPrimitive("1680623937705"), JsonPrimitive("off")))
        for (reply in listOf(JsonArray(emptyList()), JsonArray(listOf(row, row)), JsonObject(emptyMap()),
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("1680623937705"), JsonPrimitive("unexpected"))))))) {
            val f = Fixture()
            f.timerResponse = { reply }
            assertFailsWith<ScheduleException> { f.set(true) }
            assertEquals(0, f.timerWrites); assertEquals(0, f.jobWrites)
        }
    }

    @Test fun malformed_native_reference_is_not_mistaken_for_server_only() = runTest {
        for (name in listOf(JsonNull, JsonPrimitive(""), JsonPrimitive(123))) {
            val f = Fixture()
            val param = JsonObject(f.job!!.param!! + ("params" to JsonArray(listOf(JsonObject(f.job!!.settings!! + ("name" to name))))))
            f.job = ServerSchedule(JsonObject(f.job!!.raw + ("param" to param)))
            assertFailsWith<ScheduleException> { f.set(true) }
            assertEquals(0, f.timerReads); assertEquals(0, f.jobWrites)
        }
    }

    @Test fun read_failures_before_writes_change_nothing() = runTest {
        for (failJob in listOf(true, false)) {
            val f = Fixture()
            if (failJob) f.failJobReadAt = 1 else f.failTimerReadAt = 1
            assertFailsWith<ScheduleException> { f.set(true) }
            assertEquals(0, f.timerWrites); assertEquals(0, f.jobWrites)
        }
    }

    @Test fun failed_robot_verification_never_saves_server_or_rolls_back() = runTest {
        for (unreadable in listOf(true, false)) {
            val f = Fixture()
            if (unreadable) f.failTimerReadAt = 2 else { f.applyTimer = false; f.failTimerWrite = true }
            assertFailsWith<ScheduleException> { f.set(true) }
            assertEquals(1, f.timerWrites); assertEquals(0, f.jobWrites)
            assertFalse(f.job!!.enabled)
        }
    }

    @Test fun lost_replies_are_resolved_by_readback_without_duplicate_writes() = runTest {
        val f = Fixture()
        f.failTimerWrite = true; f.failJobWrite = true
        assertTrue(f.set(true).enabled)
        assertEquals(1, f.timerWrites); assertEquals(1, f.jobWrites)
    }

    @Test fun http_failure_reports_partial_save_and_explicit_retry_skips_robot_write() = runTest {
        val f = Fixture()
        f.applyJob = false; f.failJobWrite = true
        val error = assertFailsWith<ScheduleException> { f.set(true) }
        assertTrue(error.message!!.contains("Robot timer was verified on"))
        assertTrue(error.message!!.contains("Server save failed"))
        assertTrue(f.robot); assertFalse(f.job!!.enabled)
        f.applyJob = true; f.failJobWrite = false
        assertTrue(f.set(true).enabled)
        assertEquals(1, f.timerWrites); assertEquals(2, f.jobWrites)
    }

    @Test fun http_success_without_readback_is_not_reported_as_confirmed() = runTest {
        val f = Fixture()
        f.failJobReadAt = 4
        val error = assertFailsWith<ScheduleException> { f.set(true) }
        assertTrue(error.message!!.contains("Server save is unconfirmed"))
        assertEquals(1, f.timerWrites); assertEquals(1, f.jobWrites)
    }

    @Test fun conflicts_at_each_stage_preserve_concurrent_changes() = runTest {
        for (at in 1..4) {
            val f = Fixture()
            val old = f.job!!
            f.beforeRead = { n -> if (n == at) f.job = ServerSchedule(JsonObject(f.job!!.raw + ("cron" to JsonPrimitive("20 10 ? * 1")))) }
            assertFailsWith<ScheduleException> { f.set(true, old) }
            assertEquals("20 10 ? * 1", f.job!!.cron)
            assertEquals(if (at <= 2) 0 else 1, f.timerWrites)
            assertEquals(if (at < 4) 0 else 1, f.jobWrites)
        }
    }

    @Test fun unrelated_time_edit_never_reads_or_changes_native_enablement() = runTest {
        val f = Fixture(serverEnabled = true, robotEnabled = false)
        val result = f.sync.save(f.job!!, buildJsonObject { put("cron", "0 8 ? * 1,2,3,4,5") }, false)
        assertTrue(result.enabled); assertFalse(f.robot)
        assertEquals(0, f.timerReads); assertEquals(0, f.timerWrites)
    }

    @Test fun simultaneous_requests_do_not_queue_duplicate_writes() = runTest {
        val f = Fixture()
        val gate = CompletableDeferred<Unit>()
        f.gate = gate
        val first = async { f.set(true) }
        testScheduler.runCurrent()
        assertFailsWith<ScheduleException> { f.set(true) }
        gate.complete(Unit)
        assertTrue(first.await().enabled)
        assertEquals(1, f.timerWrites); assertEquals(1, f.jobWrites)
    }


    @Test fun shared_native_references_and_multiple_settings_entries_are_rejected() = runTest {
        val shared = Fixture()
        shared.extraJobs = listOf(ServerSchedule(JsonObject(shared.job!!.raw + ("id" to JsonPrimitive(2)))))
        assertFailsWith<ScheduleException> { shared.set(true) }
        assertEquals(0, shared.timerReads); assertEquals(0, shared.timerWrites); assertEquals(0, shared.jobWrites)
        val multiple = Fixture()
        val param = JsonObject(multiple.job!!.param!! + ("params" to JsonArray(listOf(multiple.job!!.settings!!, multiple.job!!.settings!!))))
        multiple.job = ServerSchedule(JsonObject(multiple.job!!.raw + ("param" to param)))
        assertFailsWith<ScheduleException> { multiple.set(true) }
        assertEquals(0, multiple.timerWrites); assertEquals(0, multiple.jobWrites)
    }

    @Test fun server_only_failures_are_not_described_as_robot_changes() = runTest {
        val f = Fixture(native = false, serverEnabled = true)
        f.applyJob = false; f.failJobWrite = true
        val error = assertFailsWith<ScheduleException> { f.set(false) }
        assertTrue(error.message!!.contains("Robot enablement was not changed"))
        assertEquals(0, f.timerReads); assertEquals(1, f.jobWrites)
    }

    @Test fun disappearing_job_after_native_update_is_reported_as_partial() = runTest {
        val f = Fixture()
        f.beforeRead = { n -> if (n == 3) f.job = null }
        val error = assertFailsWith<ScheduleException> { f.set(true) }
        assertTrue(error.message!!.contains("Robot timer was verified on"))
        assertTrue(f.robot); assertEquals(0, f.jobWrites)
    }

    @Test fun native_rpc_uses_timer_name_in_a_nested_array() {
        assertEquals("[[\"1680623937705\",\"on\"]]", JsonArray(nativeTimerUpdateParams("1680623937705", true)).toString())
        assertEquals("[[\"1680623937705\",\"off\"]]", JsonArray(nativeTimerUpdateParams("1680623937705", false)).toString())
    }
}
