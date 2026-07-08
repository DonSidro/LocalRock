package com.kodraliu.localrock.shared.crypto

import com.kodraliu.localrock.shared.testing.fromHex
import com.kodraliu.localrock.shared.testing.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Aes128Test {

    // FIPS-197 Appendix C.1: AES-128 single-block test vector.
    @Test
    fun fips197_encryptBlock() {
        val key = "000102030405060708090a0b0c0d0e0f".fromHex()
        val plaintext = "00112233445566778899aabbccddeeff".fromHex()
        // ECB with a single 16-byte block adds one 16-byte PKCS7 pad block,
        // so the AES-only result is the first 16 bytes.
        val ciphertext = Aes128.encryptEcb(plaintext, key)
        assertEquals("69c4e0d86a7b0430d8cdb78070b4c55a", ciphertext.copyOfRange(0, 16).toHex())
        assertEquals(32, ciphertext.size) // 16 data + 16 PKCS7 pad block
    }

    @Test
    fun fips197_decryptBlock() {
        val key = "000102030405060708090a0b0c0d0e0f".fromHex()
        // Re-encrypt the known plaintext to get a valid PKCS7-padded ciphertext, then decrypt.
        val plaintext = "00112233445566778899aabbccddeeff".fromHex()
        val ct = Aes128.encryptEcb(plaintext, key)
        val pt = Aes128.decryptEcb(ct, key)
        assertTrue(pt.contentEquals(plaintext))
    }

    @Test
    fun ecb_roundTrip_variousLengths() {
        val key = "0123456789abcdef0123456789abcdef".fromHex()
        for (len in 0..40) {
            val pt = ByteArray(len) { (it xor 0x5a).toByte() }
            val ct = Aes128.encryptEcb(pt, key)
            if (len == 0) {
                assertEquals(0, ct.size)
                assertTrue(Aes128.decryptEcb(ct, key).contentEquals(pt))
            } else {
                assertEquals(((len / 16) + 1) * 16, ct.size, "ct size for len=$len")
                assertTrue(Aes128.decryptEcb(ct, key).contentEquals(pt), "round-trip len=$len")
            }
        }
    }

    // RFC 3602 §4 Case #1: AES-128-CBC, single block, no padding needed.
    @Test
    fun rfc3602_cbc_singleBlock() {
        val key = "06a9214036b8a15b512e03d534120006".fromHex()
        val iv = "3dafba429d9eb430b422da802c9fac41".fromHex()
        val pt = "Single block msg".encodeToByteArray()
        val ct = Aes128.encryptCbc(pt, key, iv)
        // RFC vector has no padding; our PKCS7 will append a full block of 0x10s.
        assertEquals("e353779c1079aeb82708942dbe77181a", ct.copyOfRange(0, 16).toHex())
        val back = Aes128.decryptCbc(ct, key, iv)
        assertTrue(back.contentEquals(pt))
    }

    @Test
    fun cbc_roundTrip_variousLengths() {
        val key = "06a9214036b8a15b512e03d534120006".fromHex()
        val iv = "3dafba429d9eb430b422da802c9fac41".fromHex()
        for (len in 1..48) {
            val pt = ByteArray(len) { (it * 7 xor 0xa5).toByte() }
            val ct = Aes128.encryptCbc(pt, key, iv)
            val back = Aes128.decryptCbc(ct, key, iv)
            assertTrue(back.contentEquals(pt), "CBC round-trip failed for len=$len")
        }
    }
}
