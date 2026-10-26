package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ServerJobCreationTest {
    private class Fixture {
        val body = Json.parseToJsonElement("""{"id":"","enabled":true,"cron":"10 9 ? * 0","param":{"method":"server_scheduled_start","params":[{"name":"native","segments":"20,17"}]}}""").jsonObject
        var jobs = emptyList<ServerSchedule>(); var writes = 0
        var timer: JsonElement = Json.parseToJsonElement("""[["native","on",1]]""")
        var apply = true; var fail = false; var timeout = false; var failRead = false; var duplicate = false
        val creation = ServerJobCreation(
            readTimers = { timer },
            createJob = {
                writes++
                if (apply) {
                    jobs = listOf(ServerSchedule(JsonObject(it + ("id" to JsonPrimitive("http-id")))))
                    if (duplicate) jobs += ServerSchedule(JsonObject(it + ("id" to JsonPrimitive("other"))))
                }
                if (timeout) withTimeout(1) { delay(2) }
                if (fail) error("HTTP reply lost")
                "http-id"
            },
            readJobs = { if (failRead) error("offline"); jobs },
        )
    }
    @Test fun confirms_creation_without_changing_payload() = runTest {
        val f = Fixture(); val saved = f.creation.confirm("native", f.body)
        assertEquals("http-id",saved.id); assertEquals(f.body - "id", saved.raw - "id"); assertEquals(1,f.writes)
    }
    @Test fun lost_http_reply_or_inner_timeout_uses_read_back_not_another_post() = runTest {
        for (timeout in listOf(true,false)) {
            val f = Fixture(); f.fail = !timeout; f.timeout = timeout
            assertEquals("http-id",f.creation.confirm("native",f.body).id); assertEquals(1,f.writes)
        }
    }
    @Test fun failed_verification_does_not_post() = runTest {
        val f = Fixture(); f.timer = Json.parseToJsonElement("""[["native","off",1]]""")
        assertFailsWith<ScheduleException> { f.creation.confirm("native",f.body) }; assertEquals(0,f.writes)
    }
    @Test fun missing_or_duplicate_job_reports_partial_save() = runTest {
        for (duplicate in listOf(true,false)) {
            val f = Fixture(); f.duplicate = duplicate; f.apply = duplicate
            val e = assertFailsWith<ScheduleException> { f.creation.confirm("native",f.body) }
            assertTrue(e.message!!.contains("No timer was deleted")); assertEquals(1,f.writes)
        }
    }
    @Test fun failed_read_back_is_unconfirmed_without_retry() = runTest {
        val f = Fixture(); f.failRead = true
        assertFailsWith<ScheduleException> { f.creation.confirm("native",f.body) }; assertEquals(1,f.writes)
    }
}
