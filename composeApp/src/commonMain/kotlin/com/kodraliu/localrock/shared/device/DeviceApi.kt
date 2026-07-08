package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.ApiResponse
import com.kodraliu.localrock.shared.model.FirmwareOtaResponse
import com.kodraliu.localrock.shared.model.FirmwareUpdateInfo
import com.kodraliu.localrock.shared.model.Home
import com.kodraliu.localrock.shared.model.HomeDetail
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement

class DeviceApi(private val client: HttpClient) {


    suspend fun ncPrepare(): JsonElement = client.get("/nc/prepare").body()


    suspend fun getHomeDetail(token: String): HomeDetail {
        val response: ApiResponse<HomeDetail> = client.get("/api/v1/getHomeDetail") {
            header(HttpHeaders.Authorization, token)
        }.body()
        ensureOk(response)
        return response.data
    }

    suspend fun getHome(homeId: Long): Home {
        val response: ApiResponse<Home> = client.get("/v3/user/homes/$homeId").body()
        ensureOk(response)
        return response.data
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

class DeviceApiException(val responseCode: Int, message: String) : RuntimeException(message)

private fun <T> ensureOk(response: ApiResponse<T>) {
    if (response.code != 200) throw DeviceApiException(response.code, response.msg)
}
