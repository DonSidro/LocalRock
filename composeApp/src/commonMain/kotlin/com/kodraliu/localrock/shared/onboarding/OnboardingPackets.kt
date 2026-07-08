package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.crypto.Aes128
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

const val CFG_WIFI_PRE_KEY: String = "6433df70f5a3a42e"

const val CFG_WIFI_UID: String = "1234567890"

private val Compact: Json = Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
}

fun buildHelloPacket(preKey: String = CFG_WIFI_PRE_KEY, publicKeyPem: String): ByteArray {
    val body = buildJsonObject {
        put("id", 1)
        put("method", "hello")
        put("params", buildJsonObject {
            put("app_ver", 1)
            put("key", publicKeyPem)
        })
    }
    val plaintext = Compact.encodeToString(JsonObject.serializer(), body).encodeToByteArray()
    val encrypted = Aes128.encryptEcb(plaintext, preKey.encodeToByteArray())
    return CfgWifi.build(CfgWifi.CMD_HELLO, encrypted)
}


data class WifiConfigBody(
    val ssid: String,
    val password: String,
    /** Server stack URL the vacuum should phone home to, host-only with trailing `/`. */
    val serverStack: String,
    /** IANA timezone, e.g. `America/New_York`. */
    val timezone: String,
    /** POSIX TZ string for the firmware, e.g. `EST5EDT,M3.2.0,M11.1.0`. */
    val posixTz: String,
    /** 2-letter country domain, e.g. `us`. */
    val countryDomain: String,
    val tokenS: String,
    val tokenT: String,
    val uid: String = CFG_WIFI_UID,
)

fun buildWifiConfigPacket(sessionKey: String, body: WifiConfigBody): ByteArray {
    require(sessionKey.length == 16) { "session key must be 16 ASCII chars" }
    val json = buildJsonObject {
        put("u", body.uid)
        put("ssid", body.ssid)
        put("token", buildJsonObject {
            put("r", body.serverStack)
            put("tz", body.timezone)
            put("s", body.tokenS)
            put("cst", body.posixTz)
            put("t", body.tokenT)
        })
        put("passwd", body.password)
        put("country_domain", body.countryDomain)
    }
    val plaintext = Compact.encodeToString(JsonObject.serializer(), json).encodeToByteArray()
    val encrypted = Aes128.encryptEcb(plaintext, sessionKey.encodeToByteArray())
    return CfgWifi.build(CfgWifi.CMD_WIFI_CONFIG, encrypted)
}
