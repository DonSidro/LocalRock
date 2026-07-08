package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.crypto.Aes128
import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.testing.fromHex
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

    private fun gzipForTest(content: ByteArray): ByteArray {
        // Hand-rolled minimal gzip wrapping a "stored" DEFLATE block, so we don't need the
        // platform compressor to run encode-side.
        val deflate = storedDeflate(content)
        val crc = crc32(content)
        val isize = content.size.toUInt()
        val out = mutableListOf<Byte>()
        out += 0x1f.toByte(); out += 0x8b.toByte()  // magic
        out += 0x08.toByte()                        // method = deflate
        out += 0x00.toByte()                        // flags
        repeat(4) { out += 0x00.toByte() }          // mtime
        out += 0x00.toByte()                        // xfl
        out += 0xff.toByte()                        // os = unknown
        out.addAll(deflate.toList())
        for (i in 0..3) out += ((crc shr (i * 8)) and 0xffu).toInt().toByte()
        for (i in 0..3) out += ((isize shr (i * 8)) and 0xffu).toInt().toByte()
        return out.toByteArray()
    }

    private fun storedDeflate(content: ByteArray): ByteArray {
        // Single stored (BTYPE=00) block. BFINAL=1.
        val len = content.size
        require(len <= 0xffff) { "stored block too large for this test helper" }
        val out = ByteArray(5 + len)
        out[0] = 0x01 // BFINAL=1, BTYPE=00
        out[1] = (len and 0xff).toByte()
        out[2] = ((len ushr 8) and 0xff).toByte()
        out[3] = (len.inv() and 0xff).toByte()
        out[4] = ((len.inv() ushr 8) and 0xff).toByte()
        content.copyInto(out, 5)
        return out
    }

    private fun crc32(data: ByteArray): UInt {
        var c = 0xffffffffu
        for (b in data) {
            c = c xor (b.toUInt() and 0xffu)
            repeat(8) {
                c = if ((c and 1u) != 0u) (c shr 1) xor 0xedb88320u else c shr 1
            }
        }
        return c.inv()
    }
}
