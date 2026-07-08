package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.secureRandomBytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.kotlincrypto.hash.md.MD5


data class SecurityData(
    val endpoint: String,
    val nonce: ByteArray,
) {
    val nonceHex: String by lazy {
        val sb = StringBuilder(nonce.size * 2)
        for (b in nonce) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0f])
        }
        sb.toString()
    }

    override fun equals(other: Any?): Boolean =
        other is SecurityData && other.endpoint == endpoint && other.nonce.contentEquals(nonce)

    override fun hashCode(): Int = 31 * endpoint.hashCode() + nonce.contentHashCode()

    private companion object {
        val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun createSecurityData(rriotKey: String): SecurityData {
    val md = MD5().digest(rriotKey.encodeToByteArray())
    val endpointBytes = md.copyOfRange(8, 14)
    return SecurityData(
        endpoint = Base64.encode(endpointBytes),
        nonce = secureRandomBytes(16),
    )
}
