package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.crypto.Aes128
import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.testing.fromHex
import com.kodraliu.localrock.shared.testing.gzipForTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityDataTest {

    @Test
    fun endpoint_matchesPythonRoborock() {
        // Endpoint = base64(md5(rriot.k)[8:14]). Cross-checked against python-roborock.
        assertEquals("kG5Y4GcU", createSecurityData("0123456789abcdef").endpoint)
        assertEquals("HiZUpbbX", createSecurityData("zzzzzzzzzzzzzzzz").endpoint)
        assertEquals("G1eGiCNK", createSecurityData("some-session-key").endpoint)
        assertEquals("zUOuSN/e", createSecurityData("a".repeat(32)).endpoint)
    }

    @Test
    fun nonce_is16BytesAndUnique() {
        val a = createSecurityData("k")
        val b = createSecurityData("k")
        assertEquals(16, a.nonce.size)
        assertEquals(16, b.nonce.size)
        // Two calls should not produce identical nonces (CSPRNG; collision prob negligible).
        assertNotEquals(a.nonceHex, b.nonceHex)
    }

    @Test
    fun nonceHex_matchesContent() {
        val sd = SecurityData(endpoint = "ABCDEFGH", nonce = "0123456789abcdef0011223344556677".fromHex())
        assertEquals("0123456789abcdef0011223344556677", sd.nonceHex)
    }
}

class MapResponseDecoderTest {

    private val security = SecurityData(
        endpoint = "ABCDEFGH",
        nonce = "0123456789abcdef0123456789abcdef".fromHex(),
    )

    @Test
    fun roundTrip_synthetic() {
        // Build a synthetic map payload: header + AES-128-CBC(nonce, gzip(content)).
        val content = "hello-roborock-map".encodeToByteArray()
        val gzipped = gzipForTest(content)
        val encrypted = Aes128.encryptCbc(gzipped, security.nonce, ByteArray(16))
        val payload = buildPayload(security.endpoint, requestId = 0x1234, body = encrypted)

        val response = decodeMapResponse(
            RoborockMessage(
                protocol = RoborockProtocol.MAP_RESPONSE,
                payload = payload,
                seq = 1, random = 1, timestamp = 0,
            ),
            security,
        )
        assertTrue(response != null)
        assertEquals(0x1234, response.requestId)
        assertTrue(response.data.contentEquals(content))
    }

    @Test
    fun mismatchedEndpoint_returnsNull() {
        val payload = buildPayload(
            endpoint = "ZZZZZZZZ",
            requestId = 7,
            body = Aes128.encryptCbc(gzipForTest("x".encodeToByteArray()), security.nonce, ByteArray(16)),
        )
        val response = decodeMapResponse(
            RoborockMessage(RoborockProtocol.MAP_RESPONSE, payload, 1, 1, 0),
            security,
        )
        assertNull(response)
    }

    @Test
    fun tooShortPayload_throws() {
        val msg = RoborockMessage(RoborockProtocol.MAP_RESPONSE, ByteArray(10), 1, 1, 0)
        assertFailsWith<RoborockProtocolException> { decodeMapResponse(msg, security) }
    }

    private fun buildPayload(endpoint: String, requestId: Int, body: ByteArray): ByteArray {
        val header = ByteArray(24)
        endpoint.encodeToByteArray().copyInto(header, 0, endIndex = minOf(8, endpoint.length))
        header[16] = (requestId and 0xff).toByte()
        header[17] = ((requestId ushr 8) and 0xff).toByte()
        return header + body
    }
}
