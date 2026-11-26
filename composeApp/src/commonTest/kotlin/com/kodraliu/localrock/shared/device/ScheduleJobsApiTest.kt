package com.kodraliu.localrock.shared.device

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ScheduleJobsApiTest {
    @Test fun lists_result_and_data_envelopes_and_keeps_raw_settings() = runTest {
        for (key in listOf("result", "data")) {
            val client = HttpClient(MockEngine { respond("""{"code":200,"$key":[{"id":42,"cron":"10 9 ? * 0","enabled":false,"unknown":7}]}""", headers = headersOf(HttpHeaders.ContentType,"application/json")) })
            try { val jobs = ScheduleApi(client).list("robot"); assertEquals("42", jobs.single().id); assertTrue("unknown" in jobs.single().raw) }
            finally { client.close() }
        }
    }
    @Test fun rejects_application_errors_even_with_http_200() = runTest {
        val client = HttpClient(MockEngine { respond("""{"code":500,"data":[]}""") })
        try { assertFailsWith<DeviceApiException> { ScheduleApi(client).list("robot") } } finally { client.close() }
    }
    @Test fun duplicate_ids_and_invalid_shapes_are_errors_not_empty_lists() = runTest {
        for (data in listOf("{}", "[{\"id\":1},{\"id\":1}]", "[{\"cron\":\"x\"}]")) {
            val client = HttpClient(MockEngine { respond("""{"code":200,"data":$data}""") })
            try { assertFailsWith<DeviceApiException> { ScheduleApi(client).list("robot") } } finally { client.close() }
        }
    }
    @Test fun unavailable_collection_preserves_http_status_for_legacy_fallback() = runTest {
        for (status in listOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed, HttpStatusCode.Unauthorized, HttpStatusCode.InternalServerError)) {
            val client = HttpClient(MockEngine { respond("{}", status) })
            try { assertEquals(status.value, assertFailsWith<DeviceApiException> { ScheduleApi(client).list("robot") }.responseCode) } finally { client.close() }
        }
    }
    @Test fun create_accepts_a_string_id_or_the_old_job_object() = runTest {
        for ((reply, expected) in listOf("\"result\":\"77\"" to "77", "\"result\":{\"id\":78,\"cron\":\"x\"}" to "78", "\"data\":{\"id\":\"79\"}" to "79", "\"result\":{},\"data\":{\"id\":80}" to "80")) {
            val client = HttpClient(MockEngine { respond("{\"code\":200,$reply}", headers = headersOf(HttpHeaders.ContentType, "application/json")) })
            try { assertEquals(expected, ScheduleApi(client).create("robot", JsonObject(emptyMap()))) } finally { client.close() }
        }
    }
    @Test fun create_without_a_usable_id_is_still_an_error() = runTest {
        for (reply in listOf("\"result\":{}", "\"result\":\"\"", "\"result\":null", "\"result\":\"ok\"", "\"result\":\"done\"", "\"result\":true", "\"result\":{\"id\":\"ok\"}", "\"result\":[1]", "\"x\":1")) {
            val client = HttpClient(MockEngine { respond("{\"code\":200,$reply}", headers = headersOf(HttpHeaders.ContentType, "application/json")) })
            try { assertFailsWith<DeviceApiException> { ScheduleApi(client).create("robot", JsonObject(emptyMap())) } } finally { client.close() }
        }
    }
    @Test fun update_and_delete_ignore_the_result_shape() = runTest {
        for (result in listOf("\"done\"", "{\"id\":1}")) {
            val client = HttpClient(MockEngine { respond("{\"code\":200,\"result\":$result}", headers = headersOf(HttpHeaders.ContentType, "application/json")) })
            try { ScheduleApi(client).update("robot", "1", JsonObject(emptyMap())); ScheduleApi(client).delete("robot", "1") } finally { client.close() }
        }
    }
}
