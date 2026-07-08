package com.kodraliu.localrock.shared.protocol

import com.kodraliu.localrock.shared.testing.fromHex
import com.kodraliu.localrock.shared.testing.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertEquals as assertEq
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RoborockCodecTest {

    // Golden vector #1 (MQTT, no length prefix) — produced by python-roborock.
    @Test
    fun mqtt_goldenEncodeAndDecode() {
        val localKey = "0123456789abcdef"
        val msg = RoborockMessage(
            protocol = RoborockProtocol.RPC_REQUEST,
            payload = "7b226964223a312c226d6574686f64223a226765745f737461747573222c22706172616d73223a5b5d7d".fromHex(),
            seq = 42,
            random = 12345,
            timestamp = 0x65f3a200,
        )
        val expected = "312e300000002a0000303965f3a200006500303e1446a698dd03df8245c4c0a4bb077624f06b4838d63be7fddb946e68ec87bfb8f641897d2bf479c7df520ca1ffb25872b4663e"
        val codec = RoborockCodec(localKey, prefixed = false)
        assertEq(expected, codec.encode(msg).toHex())
        val decoded = RoborockCodec(localKey, prefixed = false).decode(expected.fromHex())
        assertEq(1, decoded.size)
        assertEq(msg, decoded[0])
    }

    // Golden vector #2 (local TCP, 4-byte length prefix).
    @Test
    fun local_goldenEncodeAndDecode_shortPayload() {
        val localKey = "local_key"
        val msg = RoborockMessage(
            protocol = RoborockProtocol.RPC_REQUEST,
            payload = "test_payload".encodeToByteArray(),
            seq = 1,
            random = 123,
            timestamp = 0x65f3a300,
        )
        val expected = "00000027312e30000000010000007b65f3a300006500109b8a775a746e2433f2d9088a3496a0deca5abf55"
        val codec = RoborockCodec(localKey, prefixed = true)
        assertEq(expected, codec.encode(msg).toHex())
        val decoded = RoborockCodec(localKey, prefixed = true).decode(expected.fromHex())
        assertEq(1, decoded.size)
        assertEq(msg, decoded[0])
    }

    // Golden vector #3 (local TCP, JSON-RPC payload).
    @Test
    fun local_goldenEncodeAndDecode_jsonRpc() {
        val localKey = "0123456789abcdef"
        val msg = RoborockMessage(
            protocol = RoborockProtocol.RPC_REQUEST,
            payload = "7b226964223a322c226d6574686f64223a226170705f7374617274222c22706172616d73223a5b5d7d".fromHex(),
            seq = 7,
            random = 99,
            timestamp = 0x65f3a400,
        )
        val expected = "00000047312e30000000070000006365f3a4000065003061f54f987001fb90a1aa770f9f4ea78fd519b9852a4c1344dc0f57160365e1affc1957a204b506ef24c2f44fcab0ff4e2c637c05"
        val codec = RoborockCodec(localKey, prefixed = true)
        assertEq(expected, codec.encode(msg).toHex())
        val decoded = RoborockCodec(localKey, prefixed = true).decode(expected.fromHex())
        assertEq(1, decoded.size)
        assertEq(msg, decoded[0])
    }

    @Test
    fun mqtt_skipsLeadingGarbage() {
        // python-roborock's PrefixedStruct scans forward to a valid version header.
        val codec = RoborockCodec("0123456789abcdef", prefixed = false)
        val msg = RoborockMessage(
            protocol = RoborockProtocol.RPC_REQUEST,
            payload = "test".encodeToByteArray(),
            seq = 9,
            random = 200,
            timestamp = 0x65f3a500,
        )
        val encoded = codec.encode(msg)
        val garbage = "deadbeefcafe".fromHex()
        val decoded = RoborockCodec("0123456789abcdef", prefixed = false).decode(garbage + encoded)
        assertEq(1, decoded.size)
        assertEq(msg, decoded[0])
    }

    @Test
    fun local_streamingDecoder_assemblesAcrossChunks() {
        val codec = RoborockCodec("local_key", prefixed = true)
        val msg = RoborockMessage(
            protocol = RoborockProtocol.RPC_RESPONSE,
            payload = "chunked".encodeToByteArray(),
            seq = 11,
            random = 55,
            timestamp = 0x65f3a600,
        )
        val encoded = codec.encode(msg)
        val rx = RoborockCodec("local_key", prefixed = true)
        val first = rx.decode(encoded.copyOfRange(0, 10))
        assertEq(0, first.size)
        val second = rx.decode(encoded.copyOfRange(10, encoded.size))
        assertEq(1, second.size)
        assertEq(msg, second[0])
    }

    @Test
    fun local_streamingDecoder_handlesTwoMessagesAtOnce() {
        val codec = RoborockCodec("local_key", prefixed = true)
        val msg1 = RoborockMessage(RoborockProtocol.PING_REQUEST, "a".encodeToByteArray(), 1, 1, 0x65f3b100)
        val msg2 = RoborockMessage(RoborockProtocol.PING_RESPONSE, "bb".encodeToByteArray(), 2, 2, 0x65f3b200)
        val combined = codec.encode(msg1) + codec.encode(msg2)
        val rx = RoborockCodec("local_key", prefixed = true)
        val decoded = rx.decode(combined)
        assertEq(2, decoded.size)
        assertEq(msg1, decoded[0])
        assertEq(msg2, decoded[1])
    }

    @Test
    fun crcMismatch_throws() {
        val codec = RoborockCodec("0123456789abcdef", prefixed = false)
        val msg = RoborockMessage(RoborockProtocol.RPC_REQUEST, "x".encodeToByteArray(), 1, 1, 0x65f3a200)
        val bytes = codec.encode(msg).copyOf()
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 0xff).toByte()
        assertFailsWith<RoborockProtocolException> {
            RoborockCodec("0123456789abcdef", prefixed = false).decode(bytes)
        }
    }

    @Test
    fun roundTrip_variousProtocolsAndKeys() {
        val keys = listOf("0123456789abcdef", "local_key", "zzzzzzzzzzzzzzzz")
        for (key in keys) {
            for (proto in listOf(RoborockProtocol.RPC_REQUEST, RoborockProtocol.RPC_RESPONSE, RoborockProtocol.MAP_RESPONSE)) {
                val pt = ("hello-$key-$proto").encodeToByteArray()
                val msg = RoborockMessage(proto, pt, seq = 1, random = 2, timestamp = 0x60000000)
                val mqttCodec = RoborockCodec(key, prefixed = false)
                val backMqtt = RoborockCodec(key, prefixed = false).decode(mqttCodec.encode(msg))
                assertTrue(backMqtt.size == 1 && backMqtt[0] == msg, "mqtt round-trip $key/$proto")
                val localCodec = RoborockCodec(key, prefixed = true)
                val backLocal = RoborockCodec(key, prefixed = true).decode(localCodec.encode(msg))
                assertTrue(backLocal.size == 1 && backLocal[0] == msg, "local round-trip $key/$proto")
            }
        }
    }
}
