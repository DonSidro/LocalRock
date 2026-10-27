package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.RoborockMessage
import com.kodraliu.localrock.shared.protocol.RoborockProtocol
import com.kodraliu.localrock.shared.protocol.RoborockProtocolException
import com.kodraliu.localrock.shared.testing.gzipForTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ObstaclePhotoTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun a_photo_frame_gives_its_request_id_and_image() {
        val photoData = block(1, "meta".encodeToByteArray()) + block(3, jpeg)
        val blob = decodeBlobResponse(frame(requestId = 12345, gzipForTest(photoData)))!!
        assertEquals(12345, blob.requestId)
        assertContentEquals(photoData, blob.data)
        assertContentEquals(jpeg, imageFromPhotoData(blob.data))
    }

    @Test
    fun maps_and_rpc_replies_are_not_photo_frames() {
        // A map frame starts with the security endpoint, not "ROBOROCK".
        val map = RoborockMessage(RoborockProtocol.MAP_RESPONSE, "kG5Y4GcU".encodeToByteArray() + ByteArray(40), 1, 1, 1)
        assertNull(decodeBlobResponse(map))
        val rpc = frame(1, gzipForTest(block(3, jpeg))).copy(protocol = RoborockProtocol.RPC_RESPONSE)
        assertNull(decodeBlobResponse(rpc))
    }

    @Test
    fun a_cut_off_frame_or_payload_is_an_error() {
        val whole = frame(7, gzipForTest(block(3, jpeg)))
        assertFailsWith<RoborockProtocolException> { decodeBlobResponse(whole.copy(payload = whole.payload.copyOf(whole.payload.size - 3))) }
        assertFailsWith<RoborockProtocolException> { imageFromPhotoData(block(1, "meta".encodeToByteArray())) }
        assertFailsWith<RoborockProtocolException> { imageFromPhotoData(block(3, "not an image".encodeToByteArray())) }
        assertFailsWith<RoborockProtocolException> { imageFromPhotoData(block(3, jpeg).copyOf(10)) }
    }

    @Test
    fun get_photo_params_follow_python_roborock() {
        val security = SecurityData(endpoint = "kG5Y4GcU", nonce = ByteArray(16) { it.toByte() })
        val key = JsonObject(mapOf("n" to JsonPrimitive("abc"), "e" to JsonPrimitive("010001")))
        assertEquals(
            Json.parseToJsonElement(
                """{"security":{"pub_key":{"n":"abc","e":"010001"},"cipher_suite":0},"endpoint":"kG5Y4GcU",""" +
                    """"nonce":"000102030405060708090a0b0c0d0e0f","data_filter":{"img_id":"002OP8DjMLKGBRnX","type":1}}""",
            ),
            getPhotoParams(key, security, "002OP8DjMLKGBRnX"),
        )
    }

    private fun block(type: Int, payload: ByteArray): ByteArray =
        le16(type) + le16(8) + le32(payload.size) + payload

    private fun frame(requestId: Int, gzipped: ByteArray): RoborockMessage {
        val header = "ROBOROCK".encodeToByteArray() + le32(requestId) + ByteArray(4) + le16(24) + ByteArray(2) + le32(gzipped.size)
        return RoborockMessage(RoborockProtocol.MAP_RESPONSE, header + gzipped, 1, 1, 1)
    }

    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    private fun le32(v: Int) = le16(v) + le16(v shr 16)
}
