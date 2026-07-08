package com.kodraliu.localrock.shared.protocol

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class V1EnvelopeTest {

    @Test
    fun encodeRequest_matchesPythonRoborockGolden_appStart() {
        val expected = """{"dps":{"101":"{\"id\":12345,\"method\":\"app_start\",\"params\":[]}"},"t":1710465536}"""
        val actual = V1Envelope.encodeRequest(
            requestId = 12345,
            method = "app_start",
            params = emptyList(),
            timestamp = 1710465536,
        ).decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun encodeRequest_matchesPythonRoborockGolden_getStatus() {
        val expected = """{"dps":{"101":"{\"id\":42,\"method\":\"get_status\",\"params\":[]}"},"t":1710465600}"""
        val actual = V1Envelope.encodeRequest(
            requestId = 42,
            method = "get_status",
            timestamp = 1710465600,
        ).decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun encodeRequest_matchesPythonRoborockGolden_setCustomMode() {
        val expected = """{"dps":{"101":"{\"id\":9999,\"method\":\"set_custom_mode\",\"params\":[103]}"},"t":1710465700}"""
        val actual = V1Envelope.encodeRequest(
            requestId = 9999,
            method = "set_custom_mode",
            params = listOf(JsonPrimitive(103)),
            timestamp = 1710465700,
        ).decodeToString()
        assertEquals(expected, actual)
    }

    @Test
    fun decodeResponse_resultOk() {
        // A typical {"id":42,"result":["ok"]} response, wrapped in the DPS envelope on key 102.
        val raw = """{"dps":{"102":"{\"id\":42,\"result\":[\"ok\"]}"}}""".encodeToByteArray()
        val response = V1Envelope.decodeResponse(raw)
        assertEquals(42, response.id)
        assertEquals(1, response.result?.jsonArray?.size)
        assertEquals("ok", response.result?.jsonArray?.get(0)?.jsonPrimitive?.contentOrNull)
        assertNull(response.error)
    }

    @Test
    fun decodeResponse_error() {
        val raw = """{"dps":{"102":"{\"id\":7,\"error\":{\"code\":-10007,\"message\":\"invalid status\"}}"}}""".encodeToByteArray()
        val response = V1Envelope.decodeResponse(raw)
        assertEquals(7, response.id)
        assertNull(response.result)
        val err = response.error
        assertNotNull(err)
        assertEquals(-10007, err["code"]?.jsonPrimitive?.intOrNull)
        assertEquals("invalid status", err["message"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun decodeDpsPush_extractsStatusFields() {
        // 121 = STATE, 122 = BATTERY, 123 = FAN_POWER. Push-style unsolicited update.
        val raw = """{"dps":{"121":8,"122":97,"123":102}}""".encodeToByteArray()
        val push = V1Envelope.decodeDpsPush(raw)
        assertNotNull(push)
        assertEquals(8, push[121]?.jsonPrimitive?.intOrNull)
        assertEquals(97, push[122]?.jsonPrimitive?.intOrNull)
        assertEquals(102, push[123]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun decodeDpsPush_returnsNullForGarbage() {
        assertNull(V1Envelope.decodeDpsPush("not json".encodeToByteArray()))
    }

    @Test
    fun roundTrip_throughCodec() {
        // Encode an app_start request, frame it, decrypt+decode, and re-parse the inner envelope.
        val localKey = "0123456789abcdef"
        val ts = 0x65f3a200
        val codec = RoborockCodec(localKey, prefixed = false)
        val reqPayload = V1Envelope.encodeRequest(requestId = 1, method = "app_start", timestamp = ts)
        val msg = RoborockMessage(RoborockProtocol.RPC_REQUEST, reqPayload, seq = 1, random = 2, timestamp = ts)
        val frame = codec.encode(msg)
        val decoded = RoborockCodec(localKey, prefixed = false).decode(frame)
        assertEquals(1, decoded.size)
        val inner = decoded[0].payload.decodeToString()
        assertEquals(reqPayload.decodeToString(), inner)
    }
}
