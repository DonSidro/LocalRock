package com.kodraliu.localrock.shared.onboarding


expect class RsaKeyPair {
    val publicKeyPem: String


    fun decryptPkcs1V15Blocks(ciphertext: ByteArray): ByteArray
}

class RsaException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

expect fun generateRsa1024KeyPair(): RsaKeyPair

expect fun importRsa1024KeyPair(privateKeyPkcs8Pem: String): RsaKeyPair
