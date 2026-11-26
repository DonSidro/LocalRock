package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.model.Device
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Replies are shaped like local_roborock_server's put_device (wrap_response of the device detail). */
class DeviceRenameTest {
    private var method: HttpMethod? = null
    private var path: String? = null
    private var body: String? = null
    private var contentType: ContentType? = null

    private fun client(reply: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine { request ->
        method = request.method
        path = request.url.encodedPath
        contentType = request.body.contentType
        body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        respond(reply, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) {
        install(ContentNegotiation) { json(ProdJson) }
    }

    private fun ok(name: String) = """{"success":true,"code":200,"msg":"success","data":{"duid":"abc","name":"$name"},"result":{"duid":"abc","name":"$name"}}"""

    @Test
    fun renaming_puts_a_form_with_the_trimmed_name() = runTest {
        val held = client(ok("Upstairs Curv")).renameDevice("abc", "  Upstairs Curv ")
        assertEquals(HttpMethod.Put, method)
        assertEquals("/user/devices/abc", path)
        assertEquals("name=Upstairs+Curv", body)
        assertTrue(contentType?.match(ContentType.Application.FormUrlEncoded) == true)
        assertEquals("Upstairs Curv", held)
    }

    @Test
    fun a_name_the_server_skipped_is_an_error() = runTest {
        assertFailsWith<DeviceApiException> { client(ok("Old name")).renameDevice("abc", "New name") }
    }

    @Test
    fun an_http_failure_is_an_error() = runTest {
        assertFailsWith<DeviceApiException> { client("nope", HttpStatusCode.InternalServerError).renameDevice("abc", "Name") }
    }

    @Test
    fun an_empty_or_too_long_name_is_refused_before_sending() = runTest {
        assertFailsWith<IllegalArgumentException> { client(ok("x")).renameDevice("abc", "   ") }
        assertFailsWith<IllegalArgumentException> { client(ok("x")).renameDevice("abc", "a".repeat(65)) }
        assertNull(method)
    }

    @Test
    fun validation_keeps_names_up_to_64_characters() {
        assertEquals("a".repeat(64), validateDeviceName(" " + "a".repeat(64) + " "))
        assertEquals("Kitchen", validateDeviceName("Kitchen"))
    }

    @Test
    fun the_title_follows_the_reloaded_device_list() {
        val before = listOf(Device(duid = "a", name = "Curv"), Device(duid = "b", name = "Pro"))
        assertEquals("Curv", currentDeviceName(before, "a", "Old"))
        assertEquals("Kitchen bot", currentDeviceName(before.map { if (it.duid == "a") it.copy(name = "Kitchen bot") else it }, "a", "Old"))
        assertEquals("Old", currentDeviceName(before, "z", "Old"))
        assertEquals("Old", currentDeviceName(listOf(Device(duid = "a", name = " ")), "a", "Old"))
    }
}
