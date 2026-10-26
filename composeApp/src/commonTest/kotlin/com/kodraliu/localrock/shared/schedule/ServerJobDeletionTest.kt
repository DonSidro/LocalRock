package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ServerJobDeletionTest {
    private class Fixture(native: Boolean = true) {
        val original = ServerSchedule(Json.parseToJsonElement("""{"id":"job","enabled":false,"param":{"method":"server_scheduled_start","params":[${if (native) "{\"name\":\"native\"}" else "{}"}]}}""").jsonObject)
        var jobs = listOf(original)
        var timer: JsonElement = Json.parseToJsonElement("""[["native","off",1]]""")
        var robotWrites = 0; var jobWrites = 0; var robotReads = 0
        var failRobot = false; var applyRobot = true; var failJob = false; var applyJob = true
        var afterRobot: () -> Unit = {}
        val delete = ServerJobDeletion(
            readJobs = { jobs },
            readTimers = { robotReads++; timer },
            deleteTimer = { assertEquals("native", it); robotWrites++; if (applyRobot) timer = JsonArray(emptyList()); afterRobot(); if (failRobot) error("lost MQTT reply") },
            deleteJob = { assertEquals("job", it); jobWrites++; if (applyJob) jobs = emptyList(); if (failJob) error("lost HTTP reply") },
        )
    }
    @Test fun native_delete_uses_native_id_and_verifies_both_sides() = runTest {
        val f = Fixture(); f.delete.delete(f.original)
        assertEquals(1,f.robotWrites); assertEquals(1,f.jobWrites); assertEquals(2,f.robotReads)
    }
    @Test fun server_only_deletion_never_touches_native_timers() = runTest {
        val f = Fixture(false); f.delete.delete(f.original)
        assertEquals(0,f.robotReads); assertEquals(0,f.robotWrites); assertEquals(1,f.jobWrites)
    }
    @Test fun lost_acknowledgements_are_read_back_without_duplicate_deletes() = runTest {
        val f = Fixture(); f.failRobot = true; f.failJob = true; f.delete.delete(f.original)
        assertEquals(1,f.robotWrites); assertEquals(1,f.jobWrites)
    }
    @Test fun failed_native_verification_preserves_job() = runTest {
        val f = Fixture(); f.applyRobot = false
        assertFailsWith<ScheduleException> { f.delete.delete(f.original) }
        assertEquals(0,f.jobWrites)
    }
    @Test fun partial_server_failure_is_reported_without_native_recreation() = runTest {
        val f = Fixture(); f.applyJob = false; f.failJob = true
        val error = assertFailsWith<ScheduleException> { f.delete.delete(f.original) }
        assertTrue(error.message!!.contains("Native timer deletion was confirmed"))
        assertEquals(1,f.robotWrites); assertEquals(1,f.jobWrites)
    }
    @Test fun conflict_after_native_delete_does_not_delete_modified_job() = runTest {
        val f = Fixture(); f.afterRobot = { f.jobs = listOf(ServerSchedule(JsonObject(f.original.raw + ("cron" to JsonPrimitive("changed"))))) }
        assertFailsWith<ScheduleException> { f.delete.delete(f.original) }; assertEquals(0,f.jobWrites)
    }
    @Test fun shared_native_name_blocks_all_deletion() = runTest {
        val f = Fixture(); f.jobs += ServerSchedule(JsonObject(f.original.raw + ("id" to JsonPrimitive("other"))))
        assertFailsWith<ScheduleException> { f.delete.delete(f.original) }; assertEquals(0,f.robotWrites); assertEquals(0,f.jobWrites)
    }
}
