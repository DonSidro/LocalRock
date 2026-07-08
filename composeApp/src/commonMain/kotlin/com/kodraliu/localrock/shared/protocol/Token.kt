package com.kodraliu.localrock.shared.protocol

import org.kotlincrypto.hash.md.MD5

internal val SALT: ByteArray = byteArrayOf(
    0x54, 0x58, 0x64, 0x66, 0x75, 0x24, 0x6a, 0x79,
    0x5a, 0x23, 0x54, 0x5a, 0x48, 0x73, 0x67, 0x34,
) // b"TXdfu$jyZ#TZHsg4"


internal fun encodeTimestamp(timestamp: Int): ByteArray {
    val hex = (timestamp.toLong() and 0xffffffffL).toString(16).padStart(8, '0')
    val perm = intArrayOf(5, 6, 3, 7, 1, 2, 0, 4)
    return ByteArray(8) { hex[perm[it]].code.toByte() }
}


internal fun deriveToken(localKey: String, timestamp: Int): ByteArray {
    val md = MD5()
    md.update(encodeTimestamp(timestamp))
    md.update(localKey.encodeToByteArray())
    md.update(SALT)
    return md.digest()
}
