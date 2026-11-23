package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.crypto.Aes128
import com.kodraliu.localrock.shared.onboarding.RsaKeyPair
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.protocol.gunzip
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/*
 * The official app's cipher_suite 1 for get_patrol_photo: the request carries the app's own RSA-1024 public key and
 * the robot answers with a 184-byte header whose bytes 56 to 184 are a fresh iv||AES key wrapped to that key
 * (RSA PKCS#1 v1.5), followed by the AES-CBC encrypted gzip. Header and body layout are confirmed against a capture
 * of the official app; the wrap and AES details come from the plugin's decryptRobotData and its RSA module.
 */

internal const val CIPHER_SUITE_PLAIN = 0
internal const val CIPHER_SUITE_RSA = 1

/** The placeholder endpoint the official app puts in the params; the robot echoes it at byte 24 of the header. */
internal const val OFFICIAL_PARAMS_ENDPOINT = "xxx"

internal const val OFFICIAL_HEADER_SIZE = 184
private const val WRAPPED_KEY_OFFSET = 56
private const val RSA_BLOCK = 128
private const val AES_BLOCK = 16

/** The failure of a photo request, with a message that is fit to show next to the photo. */
class PatrolPhotoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** What to show next to a photo that could not be loaded. */
internal fun patrolFailureMessage(e: Throwable): String = when (e) {
    is PatrolPhotoException -> e.message ?: "The photo could not be read"
    is TimeoutCancellationException -> "The robot didn't send the photo in time"
    is RobotCommandException -> "The robot refused the photo request (${e.message})"
    is RoborockProtocolException -> "The robot sent photo data this app can't read"
    else -> e.message ?: "The robot didn't send the photo"
}

/** The public key in the shape the official app sends it: exponent and modulus as hex strings. */
internal fun rsaPublicKeyJson(pair: RsaKeyPair): JsonObject {
    val pem = pair.publicKeyPem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("")
    val modulus = rsaModulusFromSpki(Base64.decode(pem))
    return JsonObject(mapOf("e" to JsonPrimitive("10001"), "n" to JsonPrimitive(modulus.toHex())))
}

/** The modulus of the RSAPublicKey in a SubjectPublicKeyInfo, without its sign byte. */
internal fun rsaModulusFromSpki(spki: ByteArray): ByteArray {
    val der = Der(spki)
    der.enter(0x30)
    der.skip(0x30)
    der.enter(0x03)
    der.skipByte()
    der.enter(0x30)
    return der.read(0x02).dropWhile { it == 0.toByte() }.toByteArray()
}

private class Der(private val b: ByteArray) {
    var pos = 0

    private fun header(tag: Int): Int {
        if (pos + 2 > b.size || (b[pos].toInt() and 0xff) != tag) throw RoborockProtocolException("Invalid RSA public key")
        pos++
        var len = b[pos++].toInt() and 0xff
        if (len and 0x80 != 0) {
            val count = len and 0x7f
            if (count > 3 || pos + count > b.size) throw RoborockProtocolException("Invalid RSA public key")
            len = 0
            repeat(count) { len = (len shl 8) or (b[pos++].toInt() and 0xff) }
        }
        if (len < 0 || pos + len > b.size) throw RoborockProtocolException("Invalid RSA public key")
        return len
    }

    fun enter(tag: Int) { header(tag) }

    fun skip(tag: Int) {
        val len = header(tag)
        pos += len
    }

    fun skipByte() { pos++ }

    fun read(tag: Int): ByteArray {
        val len = header(tag)
        return b.copyOfRange(pos, pos + len).also { pos += len }
    }
}

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (x in this) {
        val v = x.toInt() and 0xff
        sb.append(digits[v ushr 4]).append(digits[v and 0x0f])
    }
    return sb.toString()
}

/**
 * Decrypts a cipher_suite 1 reply to the unzipped photo data. [header] is the 184 bytes that carry the wrapped key
 * and [body] the AES-CBC ciphertext; the AES key is the unwrapped bytes after the first 16, the iv those 16.
 */
internal fun decryptOfficialBody(header: ByteArray, body: ByteArray, keys: RsaKeyPair): ByteArray {
    if (header.size < WRAPPED_KEY_OFFSET + RSA_BLOCK) throw PatrolPhotoException("The photo reply header is too short")
    return decryptWrapped(header.copyOfRange(WRAPPED_KEY_OFFSET, WRAPPED_KEY_OFFSET + RSA_BLOCK), body, keys)
}

private fun decryptWrapped(wrapped: ByteArray, body: ByteArray, keys: RsaKeyPair): ByteArray {
    val material = try {
        keys.decryptPkcs1V15Blocks(wrapped)
    } catch (e: Exception) {
        throw PatrolPhotoException("The photo's key wasn't wrapped for this app's key", e)
    }
    if (material.size != 2 * AES_BLOCK) throw PatrolPhotoException("Unexpected photo key length ${material.size}")
    if (body.isEmpty() || body.size % AES_BLOCK != 0) throw PatrolPhotoException("The photo data isn't a whole number of AES blocks")
    val plain = Aes128.decryptCbc(body, material.copyOfRange(AES_BLOCK, material.size), material.copyOfRange(0, AES_BLOCK), unpad = false)
    val unpadded = stripPkcs7(plain)
    return try {
        gunzip(unpadded)
    } catch (e: Exception) {
        throw PatrolPhotoException("The decrypted photo data isn't compressed data", e)
    }
}

private fun stripPkcs7(data: ByteArray): ByteArray {
    val pad = (data.lastOrNull()?.toInt() ?: return data) and 0xff
    if (pad !in 1..AES_BLOCK || pad > data.size) return data
    for (i in data.size - pad until data.size) if ((data[i].toInt() and 0xff) != pad) return data
    return data.copyOfRange(0, data.size - pad)
}

/**
 * A reply with no "ROBOROCK" header, as the robot sent for original photos (type 0) in the capture. Its size is 184
 * more than a whole number of AES blocks, but the wrapped key does not sit at byte 56 as in the headered frames, so
 * the layouts that fit that size are tried in turn; each is accepted only if the wrapped key unwraps and the
 * result unzips. Null if none does.
 */
internal fun decryptHeaderlessBody(payload: ByteArray, keys: RsaKeyPair): ByteArray? {
    if (payload.size < OFFICIAL_HEADER_SIZE + AES_BLOCK || (payload.size - OFFICIAL_HEADER_SIZE) % AES_BLOCK != 0) return null
    val tail = payload.size - RSA_BLOCK
    val layouts = listOf(
        // key at the standard offset, body after 184
        Triple(WRAPPED_KEY_OFFSET, OFFICIAL_HEADER_SIZE, payload.size),
        // key as the last 128 bytes, 8 bytes before the body or after it
        Triple(tail, 8, tail),
        Triple(tail, 0, tail - 8),
    )
    for ((keyAt, bodyStart, bodyEnd) in layouts) {
        val keyEnd = keyAt + RSA_BLOCK
        if (keyEnd > payload.size) continue
        val body = payload.copyOfRange(bodyStart, bodyEnd)
        val out = runCatching { decryptWrapped(payload.copyOfRange(keyAt, keyEnd), body, keys) }.getOrNull()
        if (out != null) return out
    }
    return null
}
