package com.kodraliu.localrock.shared.onboarding

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFNumberIntType
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateDecryptedData
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyGetBlockSize
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

/**
 * iOS RSA via the Security framework (SecKey). Mirrors the Android/JCA behaviour used during vacuum
 * onboarding:
 *  - [publicKeyPem] is an X.509 **SubjectPublicKeyInfo** ("BEGIN PUBLIC KEY"). SecKey's external
 *    representation is the bare PKCS#1 `RSAPublicKey`, so we wrap it in the SPKI header to match
 *    what OpenSSL/JCA emit and what the robot expects.
 *  - [decryptPkcs1V15Blocks] decrypts each modulus-sized block with RSA PKCS#1 v1.5 and concatenates.
 *
 * The two SecKeyRefs live for the object's lifetime (a single onboarding attempt) and are not
 * explicitly released — a negligible one-shot cost.
 */
@OptIn(ExperimentalForeignApi::class)
actual class RsaKeyPair internal constructor(
    private val publicKey: SecKeyRef,
    private val privateKey: SecKeyRef,
) {
    actual val publicKeyPem: String by lazy {
        val pkcs1 = memScoped {
            val err = alloc<CFErrorRefVar>()
            val data = SecKeyCopyExternalRepresentation(publicKey, err.ptr)
                ?: throw RsaException("SecKeyCopyExternalRepresentation failed: ${errMsg(err.value)}")
            val bytes = data.toByteArray()
            CFRelease(data)
            bytes
        }
        val spki = pkcs1PublicToSpki(pkcs1)
        buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            base64Encode(spki).chunked(64).forEach { append(it).append('\n') }
            append("-----END PUBLIC KEY-----")
        }
    }

    actual fun decryptPkcs1V15Blocks(ciphertext: ByteArray): ByteArray {
        val blockSize = SecKeyGetBlockSize(privateKey).toInt()
        if (blockSize <= 0) throw RsaException("RSA key reports invalid block size $blockSize")
        if (ciphertext.size % blockSize != 0) {
            throw RsaException("ciphertext size ${ciphertext.size} is not a multiple of block size $blockSize")
        }
        val out = ArrayList<Byte>(ciphertext.size)
        var off = 0
        while (off < ciphertext.size) {
            val block = ciphertext.copyOfRange(off, off + blockSize)
            memScoped {
                val err = alloc<CFErrorRefVar>()
                val input = block.toCFData()
                val decrypted = SecKeyCreateDecryptedData(
                    privateKey, kSecKeyAlgorithmRSAEncryptionPKCS1, input, err.ptr,
                )
                CFRelease(input)
                if (decrypted == null) {
                    throw RsaException("RSA PKCS#1 v1.5 decrypt failed at block ${off / blockSize}: ${errMsg(err.value)}")
                }
                val bytes = decrypted.toByteArray()
                CFRelease(decrypted)
                bytes.forEach { out.add(it) }
            }
            off += blockSize
        }
        return out.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun generateRsa1024KeyPair(): RsaKeyPair = memScoped {
    val attrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 0.convert(), null, null)
        ?: throw RsaException("CFDictionaryCreateMutable failed")
    CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeRSA)
    val bits = alloc<IntVar>().apply { value = 1024 }
    val size = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, bits.ptr)
    CFDictionaryAddValue(attrs, kSecAttrKeySizeInBits, size)

    val err = alloc<CFErrorRefVar>()
    val privateKey = SecKeyCreateRandomKey(attrs, err.ptr)
    CFRelease(size)
    CFRelease(attrs)
    if (privateKey == null) throw RsaException("SecKeyCreateRandomKey failed: ${errMsg(err.value)}")
    val publicKey = SecKeyCopyPublicKey(privateKey)
        ?: run { CFRelease(privateKey); throw RsaException("SecKeyCopyPublicKey failed") }
    RsaKeyPair(publicKey, privateKey)
}

@OptIn(ExperimentalForeignApi::class)
actual fun importRsa1024KeyPair(privateKeyPkcs8Pem: String): RsaKeyPair {
    val pkcs8 = base64Decode(pemBody(privateKeyPkcs8Pem))
    val pkcs1 = pkcs8PrivateToPkcs1(pkcs8)
    return memScoped {
        val attrs = CFDictionaryCreateMutable(kCFAllocatorDefault, 0.convert(), null, null)
            ?: throw RsaException("CFDictionaryCreateMutable failed")
        CFDictionaryAddValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionaryAddValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPrivate)

        val err = alloc<CFErrorRefVar>()
        val input = pkcs1.toCFData()
        val privateKey = SecKeyCreateWithData(input, attrs, err.ptr)
        CFRelease(input)
        CFRelease(attrs)
        if (privateKey == null) throw RsaException("SecKeyCreateWithData failed: ${errMsg(err.value)}")
        val publicKey = SecKeyCopyPublicKey(privateKey)
            ?: run { CFRelease(privateKey); throw RsaException("SecKeyCopyPublicKey failed") }
        RsaKeyPair(publicKey, privateKey)
    }
}

// ---- CoreFoundation <-> ByteArray helpers ----

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData(): CFDataRef {
    if (isEmpty()) return CFDataCreate(kCFAllocatorDefault, null, 0.convert())
        ?: throw RsaException("CFDataCreate failed")
    return usePinned { pinned ->
        CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), size.convert())
            ?: throw RsaException("CFDataCreate failed")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CFDataRef.toByteArray(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    if (length <= 0) return ByteArray(0)
    val ptr = CFDataGetBytePtr(this) ?: return ByteArray(0)
    return ByteArray(length) { ptr[it].toByte() }
}

@OptIn(ExperimentalForeignApi::class)
private fun errMsg(error: CFErrorRef?): String {
    if (error == null) return "unknown Security error"
    val code = CFErrorGetCode(error).toInt()
    CFRelease(error)
    return "Security error code=$code"
}

// ---- Minimal DER helpers (self-contained; avoids an ASN.1 dependency) ----

private val RSA_ALGORITHM_ID = byteArrayOf(
    0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(),
    0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
)

private fun derLength(len: Int): ByteArray {
    if (len < 0x80) return byteArrayOf(len.toByte())
    val bytes = ArrayList<Byte>()
    var n = len
    while (n > 0) {
        bytes.add(0, (n and 0xff).toByte())
        n = n ushr 8
    }
    return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
}

private fun derTlv(tag: Int, content: ByteArray): ByteArray =
    byteArrayOf(tag.toByte()) + derLength(content.size) + content

/** Wrap a PKCS#1 RSAPublicKey in a SubjectPublicKeyInfo so it PEM-encodes as "BEGIN PUBLIC KEY". */
private fun pkcs1PublicToSpki(pkcs1: ByteArray): ByteArray {
    val subjectPublicKey = derTlv(0x03, byteArrayOf(0x00) + pkcs1) // BIT STRING, 0 unused bits
    return derTlv(0x30, RSA_ALGORITHM_ID + subjectPublicKey)
}

/** Extract the inner PKCS#1 RSAPrivateKey from a PKCS#8 PrivateKeyInfo DER blob. */
private fun pkcs8PrivateToPkcs1(pkcs8: ByteArray): ByteArray {
    var pos = 0
    // Reads tag+length at [pos], advances pos past the length bytes, returns (tag, contentLen).
    fun readTl(): Pair<Int, Int> {
        val tag = pkcs8[pos++].toInt() and 0xff
        var len = pkcs8[pos++].toInt() and 0xff
        if (len and 0x80 != 0) {
            val count = len and 0x7f
            len = 0
            repeat(count) { len = (len shl 8) or (pkcs8[pos++].toInt() and 0xff) }
        }
        return tag to len
    }
    val (outer, _) = readTl(); require(outer == 0x30) { "PKCS#8: expected outer SEQUENCE" }
    val (version, versionLen) = readTl(); require(version == 0x02) { "PKCS#8: expected version INTEGER" }
    pos += versionLen
    val (alg, algLen) = readTl(); require(alg == 0x30) { "PKCS#8: expected algorithm SEQUENCE" }
    pos += algLen
    val (key, keyLen) = readTl(); require(key == 0x04) { "PKCS#8: expected privateKey OCTET STRING" }
    return pkcs8.copyOfRange(pos, pos + keyLen)
}

// ---- Base64 (standard alphabet, self-contained) ----

private const val B64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

private fun pemBody(pem: String): String =
    pem.lineSequence().filterNot { it.trim().startsWith("-----") }.joinToString("")

private fun base64Encode(data: ByteArray): String {
    val sb = StringBuilder((data.size + 2) / 3 * 4)
    var i = 0
    while (i + 3 <= data.size) {
        val n = ((data[i].toInt() and 0xff) shl 16) or
            ((data[i + 1].toInt() and 0xff) shl 8) or
            (data[i + 2].toInt() and 0xff)
        sb.append(B64_ALPHABET[(n shr 18) and 0x3f])
        sb.append(B64_ALPHABET[(n shr 12) and 0x3f])
        sb.append(B64_ALPHABET[(n shr 6) and 0x3f])
        sb.append(B64_ALPHABET[n and 0x3f])
        i += 3
    }
    when (data.size - i) {
        1 -> {
            val n = (data[i].toInt() and 0xff) shl 16
            sb.append(B64_ALPHABET[(n shr 18) and 0x3f])
            sb.append(B64_ALPHABET[(n shr 12) and 0x3f])
            sb.append("==")
        }
        2 -> {
            val n = ((data[i].toInt() and 0xff) shl 16) or ((data[i + 1].toInt() and 0xff) shl 8)
            sb.append(B64_ALPHABET[(n shr 18) and 0x3f])
            sb.append(B64_ALPHABET[(n shr 12) and 0x3f])
            sb.append(B64_ALPHABET[(n shr 6) and 0x3f])
            sb.append('=')
        }
    }
    return sb.toString()
}

private fun base64Decode(text: String): ByteArray {
    val out = ArrayList<Byte>(text.length / 4 * 3)
    var buffer = 0
    var bits = 0
    for (c in text) {
        if (c == '=') break
        val v = B64_ALPHABET.indexOf(c)
        if (v < 0) continue // skip whitespace / newlines
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out.add(((buffer shr bits) and 0xff).toByte())
        }
    }
    return out.toByteArray()
}
