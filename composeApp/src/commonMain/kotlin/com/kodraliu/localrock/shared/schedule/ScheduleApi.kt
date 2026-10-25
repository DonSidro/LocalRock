package com.kodraliu.localrock.shared.schedule

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.*

class ScheduleException(message: String) : RuntimeException(message)

class ScheduleApi(private val client: HttpClient) {
    private fun collection(duid: String) = "/user/devices/${duid.encodeURLPathPart()}/jobs"
    private fun item(duid: String, id: String) = "${collection(duid)}/${id.encodeURLPathPart()}"

    private suspend fun call(path: String, verb: HttpMethod, payload: JsonObject? = null): JsonElement {
        val response = client.request(path) {
            method = verb
            // Sign these exact UTF-8 bytes, not a second serialization of the object.
            if (payload != null) setBody(TextContent(payload.toString(), ContentType.Application.Json))
        }
        if (!response.status.isSuccess()) throw ScheduleException("Schedule request failed (HTTP ${response.status.value}). Check server schedule support and sign-in.")
        val body = response.body<JsonObject>()
        val code = (body["code"] as? JsonPrimitive)?.intOrNull
        if (code != 200 || (body["success"] as? JsonPrimitive)?.booleanOrNull == false) {
            throw ScheduleException("Schedule request rejected (code $code). Refresh and check your inputs.")
        }
        return body["result"]?.takeUnless { it is JsonNull } ?: body["data"] ?: JsonNull
    }

    suspend fun list(duid: String): List<ServerSchedule> {
        val data = call(collection(duid), HttpMethod.Get) as? JsonArray
            ?: throw ScheduleException("The server returned an unsupported schedule list")
        val jobs = data.map {
            val raw = it as? JsonObject ?: throw ScheduleException("Invalid schedule record")
            ServerSchedule(raw).also { job -> require(job.id.isNotBlank()) { "Schedule has no ID" } }
        }
        if (jobs.map { it.id }.distinct().size != jobs.size) throw ScheduleException("Duplicate schedule IDs; refresh the server inventory")
        return jobs
    }

    /** Catch stale screens before writing; the API does not offer an atomic client revision token. */
    private suspend fun checkCurrent(duid: String, expected: ServerSchedule) {
        val fresh = list(duid).singleOrNull { it.id == expected.id }
        if (fresh?.snapshot() != expected.snapshot()) throw ScheduleException("This schedule changed or was deleted. Refresh before editing it again.")
    }
    suspend fun update(duid: String, expected: ServerSchedule, patch: JsonObject) {
        checkCurrent(duid, expected)
        call(item(duid, expected.id), HttpMethod.Put, patch)
    }
    suspend fun create(duid: String, payload: JsonObject) {
        call(collection(duid), HttpMethod.Post, payload)
    }
    suspend fun delete(duid: String, expected: ServerSchedule) {
        checkCurrent(duid, expected)
        call(item(duid, expected.id), HttpMethod.Delete)
    }
}
