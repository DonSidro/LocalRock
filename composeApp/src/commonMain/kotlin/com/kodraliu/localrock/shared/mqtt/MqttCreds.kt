package com.kodraliu.localrock.shared.mqtt

import com.kodraliu.localrock.shared.model.Rriot
import org.kotlincrypto.hash.md.MD5

data class MqttCreds(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val username: String,
    val password: String,
)

class MqttCredsException(message: String) : RuntimeException(message)


fun deriveMqttCreds(rriot: Rriot): MqttCreds {
    val parsed = parseMqttUrl(rriot.r.m)
    return MqttCreds(
        host = parsed.host,
        port = parsed.port,
        tls = parsed.tls,
        username = md5Hex(rriot.u + ":" + rriot.k).substring(2, 10),
        password = md5Hex(rriot.s + ":" + rriot.k).substring(16),
    )
}

private data class ParsedMqttUrl(val host: String, val port: Int, val tls: Boolean)

private fun parseMqttUrl(url: String): ParsedMqttUrl {
    val schemeSep = url.indexOf("://")
    if (schemeSep < 0) throw MqttCredsException("MQTT URL missing scheme: $url")
    val scheme = url.substring(0, schemeSep).lowercase()
    val rest = url.substring(schemeSep + 3)
    val pathStart = rest.indexOfAny(charArrayOf('/', '?', '#'))
    val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
    val portSep = authority.lastIndexOf(':')
    if (portSep < 0) throw MqttCredsException("MQTT URL missing port: $url")
    val host = authority.substring(0, portSep)
    if (host.isEmpty()) throw MqttCredsException("MQTT URL has empty host: $url")
    val port = authority.substring(portSep + 1).toIntOrNull()
        ?: throw MqttCredsException("MQTT URL has invalid port: $url")
    return ParsedMqttUrl(host = host, port = port, tls = scheme == "ssl")
}

private fun md5Hex(input: String): String {
    val digest = MD5().digest(input.encodeToByteArray())
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xff
        sb.append(HEX[v ushr 4])
        sb.append(HEX[v and 0x0f])
    }
    return sb.toString()
}

private val HEX = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
)
