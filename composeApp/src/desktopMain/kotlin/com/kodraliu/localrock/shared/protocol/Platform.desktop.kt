package com.kodraliu.localrock.shared.protocol

import java.io.File
import java.security.SecureRandom

private val random = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray {
    val buf = ByteArray(size)
    random.nextBytes(buf)
    return buf
}

/** Saved under ~/.localrock/debug so a captured map or frame can be attached to a bug report. */
actual fun saveDebugBlob(name: String, bytes: ByteArray): String? = runCatching {
    val dir = File(System.getProperty("user.home"), ".localrock/debug").apply { mkdirs() }
    File(dir, name).apply { writeBytes(bytes) }.absolutePath
}.getOrNull()
