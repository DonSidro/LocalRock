package com.kodraliu.localrock.shared.onboarding

import com.kodraliu.localrock.shared.testing.fromHex
import com.kodraliu.localrock.shared.testing.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CfgWifiFrameTest {

    @Test
    fun build_emptyHello_matchesGolden() {
        val packet = CfgWifi.build(commandId = CfgWifi.CMD_HELLO, payload = ByteArray(0))
        // From gen_onboarding_vectors.py: empty cmd=16 frame.
        assertEquals("312e300000000100100000dc805c41", packet.toHex())
    }

    @Test
    fun build_smallPayload_matchesGolden() {
        val payload = "hello world!".encodeToByteArray()
        val packet = CfgWifi.build(commandId = CfgWifi.CMD_WIFI_CONFIG, payload = payload)
        assertEquals(
            "312e30000000010001000c68656c6c6f20776f726c6421a9c33061",
            packet.toHex(),
        )
    }

    @Test
    fun build_512bytePayload_matchesGolden() {
        val payload = ByteArray(512) { ((it * 31) and 0xff).toByte() }
        val packet = CfgWifi.build(commandId = CfgWifi.CMD_HELLO, payload = payload)
        // Trailing CRC32 is the only thing we can really pin tightly — easier than retyping
        // the 1KiB hex blob, but the size + last 4 bytes still pin the layout.
        assertEquals(11 + 512 + 4, packet.size)
        assertEquals("85dcd646", packet.copyOfRange(packet.size - 4, packet.size).toHex())
    }

    @Test
    fun parse_roundTrip() {
        val payload = "test_payload_42".encodeToByteArray()
        val packet = CfgWifi.build(CfgWifi.CMD_WIFI_CONFIG, payload)
        val parsed = CfgWifi.parse(packet)
        assertEquals(CfgWifi.CMD_WIFI_CONFIG, parsed.commandId)
        assertTrue(parsed.payload.contentEquals(payload))
    }

    @Test
    fun parse_rejectsBadMagic() {
        val bytes = "deadbeef0000000100100000000000".fromHex()
        assertFailsWith<CfgWifiFrameException> { CfgWifi.parse(bytes) }
    }

    @Test
    fun parse_rejectsCrcMismatch() {
        val packet = CfgWifi.build(CfgWifi.CMD_HELLO, "x".encodeToByteArray())
        val tampered = packet.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0xff).toByte()
        assertFailsWith<CfgWifiFrameException> { CfgWifi.parse(tampered) }
    }
}
