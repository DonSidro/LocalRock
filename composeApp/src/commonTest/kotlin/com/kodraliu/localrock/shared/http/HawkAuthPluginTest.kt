package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.HawkCreds
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.put
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
    fun other_bodies_fail_instead_of_being_signed_wrongly() = runTest {
        assertFails { client { }.put("/user/homes/1234/rooms") }
    }
}
