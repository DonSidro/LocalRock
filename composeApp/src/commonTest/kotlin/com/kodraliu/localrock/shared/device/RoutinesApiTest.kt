package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.http.ProdJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Payloads are shaped like local_roborock_server's routes/user/scene answers (wrap_response). */
class RoutinesApiTest {

    private var lastMethod: HttpMethod? = null
    private var lastPath: String? = null

    private fun api(body: String, status: HttpStatusCode = HttpStatusCode.OK) = DeviceApi(HttpClient(MockEngine { request ->
        lastMethod = request.method
        lastPath = request.url.encodedPath
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }) {
        install(ContentNegotiation) { json(ProdJson) }
    })

    @Test
    fun routines_are_listed_for_the_robot() = runTest {
        val scenes = api(
            """{"success":true,"code":200,"msg":"success",
               "data":[{"id":1032,"name":"Kitchen first","enabled":true,"type":"WORKFLOW","homeId":5,
                 "deviceId":"abc","deviceName":"S8","param":"{\"action\":{\"items\":[]}}",
                 "extra":{"invalidActions":[]},"tagId":"7"}],
               "result":[{"id":1032,"name":"Kitchen first","enabled":true,"type":"WORKFLOW","homeId":5,
                 "deviceId":"abc","deviceName":"S8","param":"{\"action\":{\"items\":[]}}",
                 "extra":{"invalidActions":[]},"tagId":"7"}]}""",
        ).getScenes("abc")

        assertEquals(HttpMethod.Get, lastMethod)
        assertEquals("/user/scene/device/abc", lastPath)
        assertEquals(listOf(1032L to "Kitchen first"), scenes.map { it.id to it.name })
    }

    @Test
    fun running_a_routine_posts_to_execute() = runTest {
        val result = api(
            """{"success":true,"code":200,"msg":"success",
               "data":{"accepted":true,"status":"started","sceneId":1032,"deviceId":"abc","sceneName":"Kitchen first","stepCount":2},
               "result":{"accepted":true,"status":"started","sceneId":1032,"deviceId":"abc","sceneName":"Kitchen first","stepCount":2}}""",
        ).executeScene(1032)

        assertEquals(HttpMethod.Post, lastMethod)
        assertEquals("/user/scene/1032/execute", lastPath)
        assertEquals("started", result.status)
    }

    @Test
    fun a_server_failure_is_an_error() = runTest {
        // The server answers a routine it cannot run (unknown id, unsupported step) with HTTP 500.
        assertFailsWith<DeviceApiException> {
            api("Internal Server Error", HttpStatusCode.InternalServerError).executeScene(9)
        }
    }
}
