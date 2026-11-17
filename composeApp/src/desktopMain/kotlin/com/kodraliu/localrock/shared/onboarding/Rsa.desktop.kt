package com.kodraliu.localrock.shared.onboarding

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

actual class RsaKeyPair internal constructor(
    private val publicKey: PublicKey,
    private val privateKey: PrivateKey,
) {
    actual val publicKeyPem: String by lazy {
        val encoded = Base64.getEncoder().encodeToString(publicKey.encoded)
        buildString {
            append("-----BEGIN PUBLIC KEY-----\n")
            encoded.chunked(64).forEach { append(it).append('\n') }
            append("-----END PUBLIC KEY-----")
        }
    }

    actual fun decryptPkcs1V15Blocks(ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val blockSize = (publicKey as RSAPublicKey).modulus.bitLength() / 8
        if (blockSize <= 0) throw RsaException("RSA modulus has no bit length")
        if (ciphertext.size % blockSize != 0) {
            throw RsaException("ciphertext size ${ciphertext.size} is not a multiple of block size $blockSize")
        }
        val out = ByteArrayOutputStream(ciphertext.size)
        var off = 0
        while (off < ciphertext.size) {
            val plain = try {
                cipher.doFinal(ciphertext, off, blockSize)
            } catch (e: Throwable) {
                throw RsaException("RSA PKCS#1 v1.5 decrypt failed at block ${off / blockSize}", e)
            }
            out.write(plain)
            off += blockSize
        }
        return out.toByteArray()
    }
}

actual fun generateRsa1024KeyPair(): RsaKeyPair {
    val gen = KeyPairGenerator.getInstance("RSA")
    gen.initialize(1024)
    val pair: KeyPair = gen.generateKeyPair()
    return RsaKeyPair(pair.public, pair.private)
}

actual fun importRsa1024KeyPair(privateKeyPkcs8Pem: String): RsaKeyPair {
    val privBytes = decodePem(privateKeyPkcs8Pem)
    val factory = KeyFactory.getInstance("RSA")
    val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
    val crt = privateKey as java.security.interfaces.RSAPrivateCrtKey
    val publicKey = factory.generatePublic(
        java.security.spec.RSAPublicKeySpec(crt.modulus, crt.publicExponent),
    )
    return RsaKeyPair(publicKey, privateKey)
}

private fun decodePem(pem: String): ByteArray {
    val body = pem
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\r", "")
        .replace("\n", "")
        .replace(" ", "")
    return Base64.getDecoder().decode(body)
}
