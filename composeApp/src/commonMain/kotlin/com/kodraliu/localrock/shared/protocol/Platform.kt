package com.kodraliu.localrock.shared.protocol

import okio.Buffer
import okio.GzipSource
import okio.buffer


internal expect fun secureRandomBytes(size: Int): ByteArray


expect fun saveDebugBlob(name: String, bytes: ByteArray): String?

internal fun gunzip(data: ByteArray): ByteArray {
    val src = Buffer().apply { write(data) }
    return GzipSource(src).buffer().readByteArray()
}
