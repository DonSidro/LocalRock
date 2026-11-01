package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Match the observed sdp/type field order. Only the legacy variant leaves actual CRLF literal. */
@OptIn(ExperimentalEncodingApi::class)
internal fun encodeCameraOffer(sdp: String, literalLineBreaks: Boolean): String {
    val quoted = if (literalLineBreaks) {
        // Escape each segment independently: a literal backslash-r/backslash-n stays escaped.
        "\"" + sdp.split("\r\n").joinToString("\r\n") { JsonPrimitive(it).toString().drop(1).dropLast(1) } + "\""
    } else JsonPrimitive(sdp).toString()
    return Base64.encode("{\"sdp\":$quoted,\"type\":\"offer\"}".encodeToByteArray())
}

/** Accept normal JSON and the OEM's legacy literal-CRLF string encoding; no SDP rewriting. */
@OptIn(ExperimentalEncodingApi::class)
internal fun decodeCameraAnswer(encoded: String): String = try {
    val text = Base64.decode(encoded).decodeToString()
    val normalized = buildString {
        var inString = false
        var escaped = false
        for (char in text) {
            require(!inString || char >= ' ' || (!escaped && (char == '\r' || char == '\n')))
            when {
                escaped -> { append(char); escaped = false }
                char == '\\' && inString -> { append(char); escaped = true }
                char == '"' -> { append(char); inString = !inString }
                inString && char == '\r' -> append("\\r")
                inString && char == '\n' -> append("\\n")
                else -> append(char)
            }
        }
    }
    val obj = Json.parseToJsonElement(normalized) as? JsonObject
        ?: throw IllegalArgumentException()
    val sdp = (obj["sdp"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    require(obj["type"] == JsonPrimitive("answer") && sdp != null)
    sdp
} catch (_: Exception) {
    throw CameraFailure("The robot returned an invalid video answer.")
}
