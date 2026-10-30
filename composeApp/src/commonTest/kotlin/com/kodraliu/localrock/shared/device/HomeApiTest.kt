package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.http.ProdJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Error bodies are written like local_roborock_server's protocol auth failures (code 40101 / 2010). */
class HomeApiTest {

    private fun api(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = DeviceApi(HttpClient(MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
    }) {
        install(ContentNegotiation) { json(ProdJson) }
    })

    @Test
    fun home_is_returned_from_the_data_field() = runTest {
        val home = api("""{"success":true,"code":200,"msg":"success","data":{"id":5,"name":"Home"}}""").getHome(5)

        assertEquals(5L, home.id)
        assertEquals("Home", home.name)
    }

    @Test
    fun auth_errors_show_the_servers_message_instead_of_a_decoding_error() = runTest {
        val e = assertFailsWith<DeviceApiException> {
            api(
                """{"code":40101,"msg":"authentication_required","data":{"reason":"invalid_hawk","auth":"hawk"}}""",
                HttpStatusCode.Unauthorized,
            ).getHome(5)
        }

        assertEquals(40101, e.responseCode)
        assertEquals("HTTP 401, code 40101: authentication_required (invalid_hawk)", e.message)
    }

    @Test
    fun non_json_errors_show_the_http_status_and_body() = runTest {
        val e = assertFailsWith<DeviceApiException> {
            api("Bad Gateway", HttpStatusCode.BadGateway, "text/plain").getHome(5)
        }

        assertEquals(502, e.responseCode)
        assertEquals("HTTP 502: Bad Gateway", e.message)
    }

    @Test
    fun create_room_reports_server_errors_too() = runTest {
        val e = assertFailsWith<DeviceApiException> {
            api(
                """{"code":40301,"msg":"new_connections_disabled","data":{"reason":"new_connections_disabled"}}""",
                HttpStatusCode.Forbidden,
            ).createRoom(5, "Kitchen")
        }

        assertEquals("HTTP 403, code 40301: new_connections_disabled", e.message)
    }
}
