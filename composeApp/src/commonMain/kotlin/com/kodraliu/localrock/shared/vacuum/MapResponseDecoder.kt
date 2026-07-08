package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.crypto.Aes128
import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.protocol.gunzip

data class MapResponse(val requestId: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is MapResponse && other.requestId == requestId && other.data.contentEquals(data)
    override fun hashCode(): Int = 31 * requestId + data.contentHashCode()
}


fun decodeMapResponse(message: RoborockMessage, security: SecurityData): MapResponse? {
    val payload = message.payload
    if (payload.size < 24) throw RoborockProtocolException("Map response payload too short: ${payload.size}")
    val endpoint = payload.decodeToString(0, 8)
    if (!endpoint.startsWith(security.endpoint)) return null
    val requestId = (payload[16].toInt() and 0xff) or ((payload[17].toInt() and 0xff) shl 8)
    val body = payload.copyOfRange(24, payload.size)
    val iv = ByteArray(16) // python-roborock's decrypt_cbc uses an all-zero IV
    val decrypted = Aes128.decryptCbc(body, key = security.nonce, iv = iv)
    val decompressed = gunzip(decrypted)
    return MapResponse(requestId = requestId, data = decompressed)
}
