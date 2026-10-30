package com.kodraliu.localrock.shared.auth

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
import kotlin.test.assertNull

/**
 * Payloads are written like local_roborock_server's login answers (wrap_response / new_connections_disabled).
 * Error bodies are built in https://github.com/Python-roborock/local_roborock_server/blob/206d560f622836fc0a58f221e042858c475564cc/src/roborock_local_server/server.py#L807-L855
 */
class LoginApiTest {

    private fun api(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        contentType: String = "application/json",
    ) = LoginApi(HttpClient(MockEngine {
        respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
    }) {
        install(ContentNegotiation) { json(ProdJson) }
    })

    @Test
    fun successful_login_returns_the_user() = runTest {
        val user = api(
            """{"success":true,"code":200,"msg":"success",
               "data":{"uid":42,"token":"tok","rruid":"rr-1",
                 "rriot":{"u":"u1","s":"s1","h":"h1","k":"k1",
                   "r":{"r":"EU","a":"https://api","m":"ssl://mqtt","l":"https://log"}}}}""",
        ).login("a@b.c", "123456")

        assertEquals(42L, user.uid)
        assertEquals("tok", user.token)
        assertEquals("rr-1", user.rruid)
    }

    @Test
    fun disabled_new_connections_report_status_code_and_message() = runTest {
        val e = assertFailsWith<LoginException> {
            api(
                """{"code":40301,"msg":"new_connections_disabled",
                   "data":{"reason":"new_connections_disabled","flow":"login"}}""",
                HttpStatusCode.Forbidden,
            ).login("a@b.c", "123456")
        }

        assertEquals(403, e.httpStatus)
        assertEquals(40301, e.responseCode)
        assertEquals("new_connections_disabled", e.serverMessage)
        assertEquals("HTTP 403, code 40301: new_connections_disabled", e.message)
    }

    @Test
    fun non_json_body_is_reported_with_the_http_status() = runTest {
        val e = assertFailsWith<LoginException> {
            api("Bad Gateway", HttpStatusCode.BadGateway, "text/plain").login("a@b.c", "123456")
        }

        assertEquals(502, e.httpStatus)
        assertNull(e.responseCode)
        assertEquals("HTTP 502: Bad Gateway", e.message)
    }

    @Test
    fun success_without_data_fails_with_a_login_exception() = runTest {
        val e = assertFailsWith<LoginException> {
            api("""{"code":200,"msg":"success"}""").login("a@b.c", "123456")
        }

        assertEquals(200, e.httpStatus)
        assertEquals(200, e.responseCode)
        assertEquals("HTTP 200, code 200: Login response contained no data", e.message)
    }

    @Test
    fun invalid_credentials_report_status_code_and_message() = runTest {
        val e = assertFailsWith<LoginException> {
            api(
                """{"code":2010,"msg":"invalid_credentials",
                   "data":{"reason":"invalid_login_pin","auth":"code"}}""",
                HttpStatusCode.Unauthorized,
            ).login("a@b.c", "000000")
        }

        assertEquals(401, e.httpStatus)
        assertEquals(2010, e.responseCode)
        assertEquals("invalid_credentials (invalid_login_pin)", e.serverMessage)
        assertEquals("HTTP 401, code 2010: invalid_credentials (invalid_login_pin)", e.message)
    }

    @Test
    fun reason_that_repeats_the_message_is_not_appended() = runTest {
        val e = assertFailsWith<LoginException> {
            api(
                """{"code":40301,"msg":"new_connections_disabled",
                   "data":{"reason":"new_connections_disabled","flow":"login"}}""",
                HttpStatusCode.Forbidden,
            ).login("a@b.c", "123456")
        }

        assertEquals("new_connections_disabled", e.serverMessage)
    }

    @Test
    fun unusable_data_leaves_the_message_alone() = runTest {
        for (data in listOf("null", """["reason"]""", """{"error":"x"}""", """{"reason":42}""", """{"reason":""}""")) {
            val e = assertFailsWith<LoginException> {
                api("""{"code":40023,"msg":"local_session_issue_failed","data":$data}""", HttpStatusCode.BadRequest)
                    .login("a@b.c", "123456")
            }

            assertEquals("local_session_issue_failed", e.serverMessage, "data=$data")
        }
    }
}
