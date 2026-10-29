package com.kodraliu.localrock.shared.auth

import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.settings.AppSettings
import com.russhwolf.settings.MapSettings
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
import kotlin.test.assertNull

/** Which failed re-logins may forget the stored email/code. Bodies mirror local_roborock_server's error payloads. */
class AuthRepositoryTest {

    private val settings = AppSettings(MapSettings()).also { it.writeLoginCredentials("a@b.c", "123456") }

    private fun repo(
        body: String,
        status: HttpStatusCode,
        contentType: String = "application/json",
    ) = AuthRepository(
        settings,
        LoginApi(HttpClient(MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
        }) {
            install(ContentNegotiation) { json(ProdJson) }
        }),
    )

    private suspend fun assertCredentialsKept(body: String, status: HttpStatusCode, contentType: String = "application/json") {
        assertNull(repo(body, status, contentType).reLogin())
        assertEquals("a@b.c" to "123456", settings.readLoginCredentials())
    }

    @Test
    fun rejected_credentials_are_forgotten() = runTest {
        val repo = repo(
            """{"code":2010,"msg":"invalid_credentials","data":{"reason":"invalid_login_pin","auth":"code"}}""",
            HttpStatusCode.Unauthorized,
        )

        assertNull(repo.reLogin())
        assertNull(settings.readLoginCredentials())
    }

    @Test
    fun disabled_new_connections_keep_the_credentials() = runTest {
        assertCredentialsKept(
            """{"code":40301,"msg":"new_connections_disabled","data":{"reason":"new_connections_disabled","flow":"login"}}""",
            HttpStatusCode.Forbidden,
        )
    }

    /** Built in https://github.com/Python-roborock/local_roborock_server/blob/206d560f622836fc0a58f221e042858c475564cc/src/roborock_local_server/bundled_backend/https_server/routes/auth/service.py#L107-L120 */
    @Test
    fun server_not_ready_keeps_the_credentials() = runTest {
        assertCredentialsKept(
            """{"code":41201,"msg":"cloud_user_data_required",
               "data":{"reason":"cloud_snapshot_missing","hint":"Fetch cloud data once ...",
                 "required_snapshot":"/data/cloud.json","missing_fields":["rriot"]}}""",
            HttpStatusCode.PreconditionFailed,
        )
    }

    @Test
    fun server_errors_with_an_envelope_keep_the_credentials() = runTest {
        assertCredentialsKept("""{"code":500,"msg":"internal_error"}""", HttpStatusCode.InternalServerError)
    }

    @Test
    fun non_json_errors_keep_the_credentials() = runTest {
        assertCredentialsKept("Bad Gateway", HttpStatusCode.BadGateway, "text/plain")
    }
}
