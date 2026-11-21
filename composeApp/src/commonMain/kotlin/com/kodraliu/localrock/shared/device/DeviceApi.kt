package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.http.serverErrorText
import com.kodraliu.localrock.shared.model.FirmwareOtaResponse
import com.kodraliu.localrock.shared.model.FirmwareUpdateInfo
import com.kodraliu.localrock.shared.model.Home
import com.kodraliu.localrock.shared.model.HomeDetail
import com.kodraliu.localrock.shared.model.Room
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.model.SceneRunResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class DeviceApi(private val client: HttpClient) {


    suspend fun ncPrepare(): JsonElement = client.get("/nc/prepare").body()


    suspend fun getHomeDetail(token: String): HomeDetail {
        return client.get("/api/v1/getHomeDetail") {
            header(HttpHeaders.Authorization, token)
        }.unwrapData()
    }

    suspend fun getHome(homeId: Long): Home {
        return client.get("/v3/user/homes/$homeId").unwrapData()
    }

    /**
     * Create a cloud room, or get the existing one with the same name (the server matches names
     * case-insensitively). Returns the room id the robot's segment table should point at.
     */
    suspend fun createRoom(homeId: Long, name: String): Room {
        // Only `data` is read: the server puts the bare room id in `result` for the official app.
        return client.submitForm(
            url = "/user/homes/$homeId/rooms",
            formParameters = parameters { append("name", name) },
        ).unwrapData()
    }

    /** The routines saved for one robot, in the order the server keeps them. */
    suspend fun getScenes(duid: String): List<Scene> {
        val resp = client.get("/user/scene/device/$duid")
        if (!resp.status.isSuccess()) {
            throw DeviceApiException(resp.status.value, "Routines unavailable (HTTP ${resp.status.value})")
        }
        val response: SceneListResponse = resp.body()
        if (response.code != 200) throw DeviceApiException(response.code, response.msg)
        return response.result ?: response.data ?: emptyList()
    }

    /** Asks the server to run a routine; the server sends the robot each step itself. */
    suspend fun executeScene(sceneId: Long): SceneRunResult {
        val resp = client.post("/user/scene/$sceneId/execute")
        if (!resp.status.isSuccess()) {
            throw DeviceApiException(resp.status.value, "The server could not run the routine (HTTP ${resp.status.value})")
        }
        val response: SceneRunResponse = resp.body()
        if (response.code != 200) throw DeviceApiException(response.code, response.msg)
        return response.result ?: response.data ?: SceneRunResult()
    }

    suspend fun checkFirmwareUpdate(duid: String): FirmwareUpdateInfo? {
        val resp = client.get("/ota/firmware/$duid/updatev2") {
            parameter("lang", "en")
            header(HttpHeaders.Accept, "application/json")
        }
        if (!resp.status.isSuccess()) {
            throw DeviceApiException(resp.status.value, "Firmware check unavailable (HTTP ${resp.status.value})")
        }
        return resp.body<FirmwareOtaResponse>().result
    }
}

/** Only `code`/`msg` are guaranteed; `data` is absent or shaped differently on error replies. */
@Serializable
private data class DataEnvelope(
    val code: Int = 0,
    val msg: String = "",
    val data: JsonElement? = null,
)

@Serializable
private data class SceneListResponse(
    val code: Int,
    val msg: String = "",
    val data: List<Scene>? = null,
    val result: List<Scene>? = null,
)

@Serializable
private data class SceneRunResponse(
    val code: Int,
    val msg: String = "",
    val data: SceneRunResult? = null,
    val result: SceneRunResult? = null,
)

class DeviceApiException(val responseCode: Int, message: String) : RuntimeException(message)

/**
 * Reads the `{code, msg, data}` envelope before `data` is decoded as [T]: an error reply (e.g. 401
 * authentication_required) carries a different `data`, and decoding it as [T] up front would hide
 * the server's own message behind a "fields are required" serialization error.
 */
private suspend inline fun <reified T> HttpResponse.unwrapData(): T {
    val httpStatus = status.value
    val text = bodyAsText()
    val envelope = try {
        ProdJson.decodeFromString<DataEnvelope>(text)
    } catch (e: SerializationException) {
        throw DeviceApiException(httpStatus, "HTTP $httpStatus: ${text.replace(Regex("\\s+"), " ").trim().take(120)}")
    }
    if (envelope.code != 200 || !status.isSuccess()) {
        throw DeviceApiException(envelope.code, "HTTP $httpStatus, code ${envelope.code}: ${serverErrorText(envelope.msg, envelope.data)}")
    }
    val data = envelope.data
        ?: throw DeviceApiException(envelope.code, "HTTP $httpStatus, code ${envelope.code}: response contained no data")
    return ProdJson.decodeFromJsonElement<T>(data)
}
