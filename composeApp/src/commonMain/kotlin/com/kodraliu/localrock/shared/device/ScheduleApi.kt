package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.vacuum.ScheduleMirror
import io.ktor.client.HttpClient
import com.kodraliu.localrock.shared.schedule.ServerSchedule
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
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
    private fun collection(duid: String) = "/user/devices/${duid.encodeURLPathPart()}/jobs"
    private fun item(duid: String, id: String) = "${collection(duid)}/${id.encodeURLPathPart()}"

    override suspend fun list(duid: String): List<ServerSchedule> {
        val body = checked(client.get(collection(duid)))
        val data = body["result"]?.takeUnless { it is JsonNull } ?: body["data"]
        val rows = data as? JsonArray ?: throw DeviceApiException(0, "Unsupported schedule list")
        val jobs = rows.map {
            val raw = it as? JsonObject ?: throw DeviceApiException(0, "Invalid schedule record")
            val id = (raw["id"] as? JsonPrimitive)?.contentOrNull
            if (id.isNullOrBlank()) throw DeviceApiException(0, "Schedule has no ID")
            ServerSchedule(raw)
        }
        if (jobs.map { it.id }.distinct().size != jobs.size) throw DeviceApiException(0, "Duplicate schedule IDs")
        return jobs
    }


    override suspend fun create(duid: String, job: JsonObject): String {
        val body = checked(client.post(collection(duid)) { setBody(json(job)) })
        return ((body["result"] ?: body["data"]) as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            ?: throw DeviceApiException(0, "The server saved the schedule but did not return its id")
    }

    override suspend fun update(duid: String, jobId: String, job: JsonObject) {
        checked(client.put(item(duid, jobId)) { setBody(json(job)) })
    }

    override suspend fun delete(duid: String, jobId: String) {
        checked(client.delete(item(duid, jobId)))
    }

    private fun json(job: JsonObject) = TextContent(job.toString(), ContentType.Application.Json)

    /** The parsed answer, or a [DeviceApiException] carrying why the server refused. */
    private suspend fun checked(response: HttpResponse): JsonObject {
        val text = response.bodyAsText()
        val body = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val success = (body?.get("success") as? JsonPrimitive)?.contentOrNull != "false"
        val code = (body?.get("code") as? JsonPrimitive)?.intOrNull
        if (!response.status.isSuccess() || body == null || !success || (code != null && code != 200)) {
            val reason = body?.get("msg")?.jsonPrimitive?.contentOrNull ?: "HTTP ${response.status.value}"
            throw DeviceApiException(response.status.value, "The server refused the schedule: $reason")
        }
        return body
    }
}
