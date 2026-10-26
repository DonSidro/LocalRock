package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.vacuum.ScheduleMirror
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Writes the cloud's record of a schedule (`/user/devices/{duid}/jobs`) the way the official app does.
 * The JSON is serialised here, not by the client, so the Hawk signature covers the very bytes sent.
 */
class ScheduleApi(private val client: HttpClient) : ScheduleMirror {

    override suspend fun create(duid: String, job: JsonObject): String {
        val body = checked(client.post("/user/devices/$duid/jobs") { setBody(json(job)) })
        return body["data"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw DeviceApiException(0, "The server saved the schedule but did not return its id")
    }

    override suspend fun update(duid: String, jobId: String, job: JsonObject) {
        checked(client.put("/user/devices/$duid/jobs/$jobId") { setBody(json(job)) })
    }

    override suspend fun delete(duid: String, jobId: String) {
        checked(client.delete("/user/devices/$duid/jobs/$jobId"))
    }

    private fun json(job: JsonObject) = TextContent(job.toString(), ContentType.Application.Json)

    /** The parsed answer, or a [DeviceApiException] carrying why the server refused. */
    private suspend fun checked(response: HttpResponse): JsonObject {
        val text = response.bodyAsText()
        val body = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val success = (body?.get("success") as? JsonPrimitive)?.contentOrNull != "false"
        if (!response.status.isSuccess() || body == null || !success) {
            val reason = body?.get("msg")?.jsonPrimitive?.contentOrNull ?: "HTTP ${response.status.value}"
            throw DeviceApiException(response.status.value, "The server refused the schedule: $reason")
        }
        return body
    }
}
