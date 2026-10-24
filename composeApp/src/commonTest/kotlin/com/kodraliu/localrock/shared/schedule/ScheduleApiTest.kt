package com.kodraliu.localrock.shared.schedule

import com.kodraliu.localrock.shared.auth.Hawk
import com.kodraliu.localrock.shared.auth.HawkCreds
import com.kodraliu.localrock.shared.auth.HawkPayload
import com.kodraliu.localrock.shared.http.HawkAuth
import com.kodraliu.localrock.shared.http.ProdJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ScheduleApiTest {
    private val creds = HawkCreds("test-user", "test-session", "test-key")
    private val raw = Json.parseToJsonElement("""{"id":42,"cron":"0 8 ? * 1","timeZoneId":"UTC","enabled":true}""").jsonObject
    private val requests = mutableListOf<Pair<HttpMethod, String>>()
    private fun api(failWrite: Boolean = false, fresh: JsonObject = raw) = ScheduleApi(HttpClient(MockEngine { request ->
        requests.add(request.method to request.url.encodedPath)
        val bytes = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()
        val expected = Hawk.authorizationHeader(creds, request.url.encodedPath, 1700000000, "schedule-test",
            payload = if (bytes != null && bytes.isNotEmpty()) HawkPayload.Json(bytes) else HawkPayload.None)
        assertEquals(expected, request.headers[HttpHeaders.Authorization])
        val body = if (request.method == HttpMethod.Get) """{"code":200,"result":[$fresh]}"""
            else if (failWrite) """{"code":400,"success":false,"result":null}"""
            else """{"code":200,"success":true,"result":{"id":42}}"""
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }) {
        install(ContentNegotiation) { json(ProdJson) }
        install(HawkAuth) { credsProvider = { creds }; nowSeconds = { 1700000000 }; nonceProvider = { "schedule-test" } }
    })
    @Test fun imported_jobs_use_rest_and_updates_are_signed() = runTest {
        val api = api()
        val job = api.list("robot").single()
        api.update("robot", job, buildJsonObject { put("enabled", false) })
        assertEquals(HttpMethod.Put to "/user/devices/robot/jobs/42", requests.last())
        api.delete("robot", job)
        assertEquals(HttpMethod.Delete to "/user/devices/robot/jobs/42", requests.last())
        api.create("robot", buildJsonObject { put("id", 99); put("name", "Pièce à vivre") })
        assertEquals(HttpMethod.Post to "/user/devices/robot/jobs", requests.last())
    }
    @Test fun application_error_is_not_success() = runTest {
        assertFailsWith<ScheduleException> { api(failWrite = true).update("robot", ServerSchedule(raw), buildJsonObject { put("enabled", false) }) }
    }
    @Test fun stale_edits_do_not_write() = runTest {
        val api = api(fresh = JsonObject(raw + ("enabled" to JsonPrimitive(false))))
        assertFailsWith<ScheduleException> { api.update("robot", ServerSchedule(raw), buildJsonObject { put("cron", "10 8 ? * 1") }) }
        assertEquals(listOf(HttpMethod.Get to "/user/devices/robot/jobs"), requests)
    }
}
