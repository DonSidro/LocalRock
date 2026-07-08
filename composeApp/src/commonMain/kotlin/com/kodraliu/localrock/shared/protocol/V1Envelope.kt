package com.kodraliu.localrock.shared.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put


object V1Envelope {

    private val Compact = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encodeRequest(
        requestId: Int,
        method: String,
        params: List<JsonElement> = emptyList(),
        timestamp: Int,
        extraInner: Map<String, JsonElement> = emptyMap(),
    ): ByteArray = encodeRequestRaw(requestId, method, JsonArray(params), timestamp, extraInner)


    fun encodeRequestRaw(
        requestId: Int,
        method: String,
        params: JsonElement,
        timestamp: Int,
        extraInner: Map<String, JsonElement> = emptyMap(),
    ): ByteArray {
        val inner =
            buildJsonObject {
                put("id", requestId)
                put("method", method)
                put("params", params)
                for ((k, v) in extraInner) put(k, v)
            }

        val innerString = Compact.encodeToString(JsonObject.serializer(), inner)
        val outer = buildJsonObject {
            put("dps", buildJsonObject { put(DPS_REQUEST, innerString) })
            put("t", timestamp)
        }
        return Compact.encodeToString(JsonObject.serializer(), outer).encodeToByteArray()
    }

    fun decodeResponse(payload: ByteArray): V1Response {
        val outer = Compact.parseToJsonElement(payload.decodeToString()).jsonObject
        val dps = outer["dps"]?.jsonObject ?: throw RoborockProtocolException("V1 response missing 'dps'")
        // Prefer "102" (RPC_RESPONSE); fall back to any single key for unsolicited dps pushes.
        val innerString = (dps[DPS_RESPONSE] ?: dps.values.firstOrNull())
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
            ?: throw RoborockProtocolException("V1 response missing inner string")
        val inner = Compact.parseToJsonElement(innerString).jsonObject
        val id = inner["id"]?.jsonPrimitive?.intOrNull
        val result = inner["result"]
        val error = inner["error"]?.jsonObject
        return V1Response(id = id, result = result, error = error)
    }


    fun decodeDpsPush(payload: ByteArray): Map<Int, JsonElement>? {
        val outer = runCatching { Compact.parseToJsonElement(payload.decodeToString()) }.getOrNull() as? JsonObject
            ?: return null
        val dps = outer["dps"] as? JsonObject ?: return null
        val out = mutableMapOf<Int, JsonElement>()
        for ((k, v) in dps) {
            val code = k.toIntOrNull() ?: continue
            out[code] = v
        }
        return out.takeIf { it.isNotEmpty() }
    }

    const val DPS_REQUEST = "101"
    const val DPS_RESPONSE = "102"
}

data class V1Response(
    val id: Int?,
    val result: JsonElement?,
    val error: JsonObject?,
)
