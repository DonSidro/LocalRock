package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.HawkCreds
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * The expected MACs were computed with local_roborock_server's own `_build_hawk_mac`
 * (bundled_backend/shared/protocol_auth.py, commit af9cc40) for the same inputs, so these tests
 * check the plugin against what the server will actually accept.
 */
class HawkAuthPluginTest {

    private val creds = HawkCreds(id = "rrid123", session = "sess456", key = "key789")

    private fun client(onAuth: (String?) -> Unit) = HttpClient(MockEngine { request ->
        onAuth(request.headers[HttpHeaders.Authorization])
        respond("{}")
    }) {
        install(HawkAuth) {
            credsProvider = { creds }
            nowSeconds = { 1_700_000_000L }
            nonceProvider = { "abcdef0123456789" }
        }
    }

    private fun macOf(header: String?): String =
        Regex("mac=\"([^\"]+)\"").find(header ?: "")?.groupValues?.get(1) ?: error("no mac in $header")

    @Test
    fun form_post_is_signed_with_the_body() = runTest {
        var auth: String? = null
        client { auth = it }.submitForm(
            url = "/user/homes/1234/rooms",
            formParameters = parameters { append("name", "Living room") },
        )
        assertEquals("3d3FLMuOgRC1+emN4OjUxcLCIwXICn4OrRwQxOZGtik=", macOf(auth))
    }

    @Test
    fun form_post_with_another_name_gets_a_different_mac() = runTest {
        var auth: String? = null
        client { auth = it }.submitForm(
            url = "/user/homes/1234/rooms",
            formParameters = parameters { append("name", "Stue med sofa") },
        )
        assertEquals("tjK8phtSyg7CMl7gZsXrzX83dMSsQVHN4lWLoH+A9ss=", macOf(auth))
    }

    @Test
    fun get_is_still_signed() = runTest {
        var auth: String? = null
        client { auth = it }.get("/v3/user/homes/1234")
        assertTrue(auth!!.startsWith("Hawk id=\"rrid123\",s=\"sess456\",ts=\"1700000000\",nonce=\"abcdef0123456789\",mac=\""))
    }

    @Test
    fun bodyless_post_is_signed_with_an_empty_body() = runTest {
        // Running a routine is a POST with no body, as python-roborock sends it.
        var auth: String? = null
        client { auth = it }.post("/user/scene/42/execute")
        assertEquals("X48lQ9deBiTvJy6d/0jLhSObKeZr72IlvtijWz9hT0M=", macOf(auth))
    }

    // Independent vectors using Python hashlib/hmac and the server's raw JSON body rule.
    @Test
    fun json_put_signs_exact_utf8_bytes() = runTest {
        var auth: String? = null
        client { auth = it }.put("/user/devices/robot/jobs/42") {
            setBody(TextContent("""{"enabled":false,"label":"Pièce à vivre"}""", ContentType.Application.Json))
        }
        assertEquals("FfrrgyDWcBwOAcLKRBkt2FdSIi+tF3buu+W1ea3KxzA=", macOf(auth))
    }

    @Test
    fun bodyless_delete_is_signed() = runTest {
        var auth: String? = null
        client { auth = it }.delete("/user/devices/robot/jobs/42")
        assertEquals("tTOA8Tqky/Zd2ops6CoGVVXo/R3qpKBy12eCrZEBDkE=", macOf(auth))
    }

    @Test
    fun other_bodies_fail_instead_of_being_signed_wrongly() = runTest {
        assertFails { client { }.put("/user/homes/1234/rooms") }
    }
}

