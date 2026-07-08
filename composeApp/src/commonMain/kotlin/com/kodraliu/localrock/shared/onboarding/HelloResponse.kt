package com.kodraliu.localrock.shared.onboarding

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


fun parseHelloResponse(packet: ByteArray, keyPair: RsaKeyPair): String {
    val frame = CfgWifi.parse(packet)
    val plaintext = keyPair.decryptPkcs1V15Blocks(frame.payload)
    val outer: JsonObject = try {
        Json.parseToJsonElement(plaintext.decodeToString()).jsonObject
    } catch (e: Throwable) {
        throw CfgWifiFrameException("hello response is not valid JSON: ${e.message}")
    }
    val params = outer["params"]?.jsonObject
        ?: throw CfgWifiFrameException("hello response missing 'params'")
    val sessionKey = params["key"]?.jsonPrimitive?.contentOrNull
        ?: throw CfgWifiFrameException("hello response missing 'params.key'")
    if (sessionKey.length != 16) {
        throw CfgWifiFrameException("hello response session key is ${sessionKey.length} chars (expected 16)")
    }
    return sessionKey
}
