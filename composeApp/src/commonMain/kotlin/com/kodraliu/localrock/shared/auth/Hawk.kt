package com.kodraliu.localrock.shared.auth

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.kotlincrypto.hash.md.MD5
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256

data class HawkCreds(
    val id: String,
    val session: String,
    val key: String,
)

sealed class HawkPayload {
    data object None : HawkPayload()

    class Json(val bytes: ByteArray) : HawkPayload() {
        override fun equals(other: Any?): Boolean =
            other is Json && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }

    class Form(val pairs: Map<String, String>) : HawkPayload() {
        override fun equals(other: Any?): Boolean =
            other is Form && pairs == other.pairs
        override fun hashCode(): Int = pairs.hashCode()
    }
}

object Hawk {
    fun mac(
        creds: HawkCreds,
        path: String,
        ts: Long,
        nonce: String,
        query: Map<String, String> = emptyMap(),
        payload: HawkPayload = HawkPayload.None,
    ): String {
        val pathHash = md5Hex(path.encodeToByteArray())
        val queryHash = if (query.isEmpty()) "" else md5Hex(formEncode(query).encodeToByteArray())
        val bodyHash = when (payload) {
            HawkPayload.None -> ""
            is HawkPayload.Json -> md5Hex(payload.bytes)
            is HawkPayload.Form -> md5Hex(formEncode(payload.pairs).encodeToByteArray())
        }
        val prestr = "${creds.id}:${creds.session}:$nonce:$ts:$pathHash:$queryHash:$bodyHash"
        return base64(hmacSha256(creds.key.encodeToByteArray(), prestr.encodeToByteArray()))
    }

    fun authorizationHeader(
        creds: HawkCreds,
        path: String,
        ts: Long,
        nonce: String,
        query: Map<String, String> = emptyMap(),
        payload: HawkPayload = HawkPayload.None,
    ): String {
        val mac = mac(creds, path, ts, nonce, query, payload)
        return "Hawk id=\"${creds.id}\",s=\"${creds.session}\",ts=\"$ts\",nonce=\"$nonce\",mac=\"$mac\""
    }
}

private fun formEncode(pairs: Map<String, String>): String =
    pairs.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }

private fun md5Hex(bytes: ByteArray): String {
    val digest = MD5().digest(bytes)
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xFF
        sb.append(HEX[v ushr 4])
        sb.append(HEX[v and 0x0F])
    }
    return sb.toString()
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    HmacSHA256(key).doFinal(data)

@OptIn(ExperimentalEncodingApi::class)
private fun base64(bytes: ByteArray): String = Base64.encode(bytes)

private val HEX = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
)
