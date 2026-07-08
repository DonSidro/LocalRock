package com.kodraliu.localrock.shared.model

import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
private data class FixtureFile(
    @SerialName("frozen_time") val frozenTime: Long,
    @SerialName("default_headers") val defaultHeaders: Map<String, String>,
    val requests: List<RequestFixture>,
)

@Serializable
private data class RequestFixture(
    val name: String,
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val form: Map<String, String> = emptyMap(),
    @SerialName("expected_response") val expectedResponse: JsonObject,
)

class FixtureRoundTripTest {

    private val envelopeJson = Json { ignoreUnknownKeys = true }

    private val modelJson = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = false
    }

    private val fixture: FixtureFile by lazy {
        envelopeJson.decodeFromString(Fixtures.IOS_APP_INIT_V4_59_02)
    }

    private fun expected(name: String): JsonElement =
        fixture.requests.first { it.name == name }.expectedResponse

    @Test
    fun roundTrip_postAppInfo() = roundTrip<AppInfo>("post_app_info")

    @Test
    fun roundTrip_getInboxLatest() = roundTrip<InboxLatest>("get_inbox_latest")

    @Test
    fun roundTrip_getHomeData() = roundTrip<Home>("get_home_data")

    @Test
    fun roundTrip_getSceneOrder() = roundTrip<List<Long>>("get_scene_order")

    @Test
    fun roundTrip_getHomeScenes() = roundTrip<List<Scene>>("get_home_scenes")

    private inline fun <reified T> roundTrip(name: String) {
        val source = expected(name)
        val parsed = modelJson.decodeFromJsonElement<ApiResponse<T>>(source)
        val reEncoded = modelJson.encodeToJsonElement(parsed)
        val reParsed = modelJson.decodeFromJsonElement<ApiResponse<T>>(reEncoded)
        assertEquals(parsed, reParsed)
    }
}
