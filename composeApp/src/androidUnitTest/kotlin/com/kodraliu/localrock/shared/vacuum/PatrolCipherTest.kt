package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.crypto.Aes128
import com.kodraliu.localrock.shared.onboarding.RsaKeyPair
import com.kodraliu.localrock.shared.onboarding.generateRsa1024KeyPair
import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.testing.gzipForTest
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PatrolCipherTest {
    private val keys = generateRsa1024KeyPair()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    private fun le(v: Int, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }

    private fun photoData(): ByteArray =
        ByteArray(12) + le(16, 2) + le(29950, 2) + le(25150, 2) + le(640, 2) + le(480, 2) + le(90, 2) + le(png.size, 4) + png

    /** Wraps iv||key to the public key the way the robot does it for the app's key. */
    private fun wrap(pair: RsaKeyPair, material: ByteArray): ByteArray {
        val e = pair.publicKeyJson()
        val n = BigInteger(e["n"]!!.jsonPrimitive.content, 16)
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, BigInteger(e["e"]!!.jsonPrimitive.content, 16)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(material)
    }

    private fun RsaKeyPair.publicKeyJson() = rsaPublicKeyJson(this)

    private fun encryptedBody(data: ByteArray, material: ByteArray) =
        Aes128.encryptCbc(gzipForTest(data), material.copyOfRange(16, 32), material.copyOfRange(0, 16))

    private fun headeredFrame(requestId: Int, wrapped: ByteArray, body: ByteArray): RoborockMessage {
        val head = "ROBOROCK".encodeToByteArray() + le(requestId, 4) + ByteArray(4) + le(184, 2) + le(1, 2) + le(body.size, 4) +
            "xxx".encodeToByteArray()
        val header = head + ByteArray(56 - head.size) + wrapped
        assertEquals(184, header.size)
        return RoborockMessage(RoborockProtocol.MAP_RESPONSE, header + body, 1, 1, 1)
    }

    @Test
    fun public_key_is_sent_as_hex_modulus_and_exponent_10001() {
        val json = rsaPublicKeyJson(keys)
        assertEquals("10001", json["e"]!!.jsonPrimitive.content)
        val n = json["n"]!!.jsonPrimitive.content
        assertEquals(256, n.length)
        assertEquals(1024, BigInteger(n, 16).bitLength())
    }

    @Test
    fun headered_reply_round_trips_to_the_thumbnail_and_matches_its_request_id() {
        val material = Random.nextBytes(32)
        val frame = headeredFrame(412908, wrap(keys, material), encryptedBody(photoData(), material))
        val raw = assertNotNull(decodeRawBlobFrame(frame))
        assertEquals(412908, raw.requestId)
        assertEquals(184, raw.header.size)
        val data = decryptOfficialBody(raw.header, raw.body, keys)
        val thumbs = parsePatrolThumbnails(data)
        assertEquals(1, thumbs.size)
        assertContentEquals(png, thumbs[0].png)
        assertEquals(29950, thumbs[0].x)
    }

    @Test
    fun a_reply_wrapped_for_another_key_fails_with_a_clear_message() {
        val material = Random.nextBytes(32)
        val frame = headeredFrame(1, wrap(generateRsa1024KeyPair(), material), encryptedBody(photoData(), material))
        val raw = decodeRawBlobFrame(frame)!!
        val e = assertFailsWith<PatrolPhotoException> { decryptOfficialBody(raw.header, raw.body, keys) }
        assertEquals("The photo's key wasn't wrapped for this app's key", e.message)
        assertFailsWith<PatrolPhotoException> { decryptOfficialBody(ByteArray(10), raw.body, keys) }
    }

    @Test
    fun headerless_frames_unwrap_in_every_layout_tried_and_garbage_does_not() {
        val material = Random.nextBytes(32)
        val wrapped = wrap(keys, material)
        val body = encryptedBody(photoData(), material)
        val standard = ByteArray(56) + wrapped + body
        assertContentEquals(photoData(), decryptHeaderlessBody(standard, keys))
        val keyAtEnd = ByteArray(8) + body + wrapped
        assertContentEquals(photoData(), decryptHeaderlessBody(keyAtEnd, keys))
        val keyAtEndPrefixless = body + ByteArray(8) + wrapped
        assertContentEquals(photoData(), decryptHeaderlessBody(keyAtEndPrefixless, keys))
        assertNull(decryptHeaderlessBody(Random.nextBytes(26968), keys))
        assertNull(decryptHeaderlessBody(Random.nextBytes(100), keys))
    }

    @Test
    fun patrol_params_for_cipher_one_match_the_official_request() {
        val security = SecurityData(endpoint = "abc", nonce = ByteArray(16))
        val p = getPatrolPhotoParams(
            rsaPublicKeyJson(keys), security, 1794607499L, 29950, 25150,
            type = PATROL_TYPE_THUMBNAILS, cipherSuite = CIPHER_SUITE_RSA, endpoint = OFFICIAL_PARAMS_ENDPOINT,
        )
        assertEquals("xxx", p["endpoint"]!!.jsonPrimitive.content)
        val sec = p["security"] as kotlinx.serialization.json.JsonObject
        assertEquals("1", sec["cipher_suite"]!!.jsonPrimitive.content)
        assertEquals("10001", (sec["pub_key"] as kotlinx.serialization.json.JsonObject)["e"]!!.jsonPrimitive.content)
    }

    @Test
    fun failures_have_readable_messages() {
        assertEquals("boom", patrolFailureMessage(PatrolPhotoException("boom")))
        assertEquals("The robot sent photo data this app can't read", patrolFailureMessage(com.kodraliu.localrock.shared.protocol.RoborockProtocolException("x")))
    }
}
