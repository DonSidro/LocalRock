package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatrolPhotoTest {
    private val key = JsonObject(mapOf("e" to JsonPrimitive("10001"), "n" to JsonPrimitive("abcd")))
    private val security = SecurityData(endpoint = "uRSrWara", nonce = ByteArray(16))

    private fun le(v: Int, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }

    private fun record(x: Int, y: Int, w: Int, h: Int, angle: Int, png: ByteArray) =
        le(16, 2) + le(x, 2) + le(y, 2) + le(w, 2) + le(h, 2) + le(angle, 2) + le(png.size, 4) + png

    @Test
    fun thumbnailRequestCarriesTaskPointAndTypeOne() {
        val p = getPatrolPhotoParams(key, security, taskId = 1794607499, x = 29950, y = 25150)
        assertEquals(1794607499L, p["task_id"]!!.jsonPrimitive.content.toLong())
        assertEquals(29950, p["x"]!!.jsonPrimitive.int)
        assertEquals(25150, p["y"]!!.jsonPrimitive.int)
        assertEquals(1, p["type"]!!.jsonPrimitive.int)
        assertEquals("uRSrWara", p["endpoint"]!!.jsonPrimitive.content)
        assertEquals(0, p["security"]!!.jsonObject["cipher_suite"]!!.jsonPrimitive.int)
        assertFalse("photo_id" in p)
    }

    @Test
    fun photoIdIsPaddedWithNulsToThirtyTwoLikeTheOfficialApp() {
        val p = getPatrolPhotoParams(key, security, 1794607499, 29950, 25150, photoId = "P.1794607499.0.2", type = PATROL_TYPE_ORIGINAL)
        val id = p["photo_id"]!!.jsonPrimitive.content
        assertEquals(32, id.length)
        assertEquals("P.1794607499.0.2", id.trimEnd(Char(0)))
        assertEquals(0, p["type"]!!.jsonPrimitive.int)
    }

    @Test
    fun zeroCoordinatesAndTaskAreLeftOut() {
        val p = getPatrolPhotoParams(key, security, taskId = 0, x = 0, y = 36050)
        assertFalse("task_id" in p)
        assertFalse("x" in p)
        assertEquals(36050, p["y"]!!.jsonPrimitive.int)
    }

    @Test
    fun thumbnailsAreReadFromRecordsAfterTheTwelveByteLead() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(9, 8, 7, 6, 5)
        val data = ByteArray(12) + record(29950, 25150, 320, 240, 90, a) + record(29950, 25150, 640, 480, 270, b)
        val thumbs = parsePatrolThumbnails(data)
        assertEquals(2, thumbs.size)
        assertEquals(listOf(29950, 25150, 320, 240, 90), thumbs[0].let { listOf(it.x, it.y, it.width, it.height, it.angle) })
        assertTrue(thumbs[0].png.contentEquals(a))
        assertTrue(thumbs[1].png.contentEquals(b))
        assertEquals(270, thumbs[1].angle)
    }

    @Test
    fun emptyPayloadHasNoThumbnailsAndATruncatedRecordIsRejected() {
        assertEquals(emptyList(), parsePatrolThumbnails(ByteArray(12)).map { it.x })
        val data = ByteArray(12) + record(1, 2, 3, 4, 5, ByteArray(10))
        assertFailsWith<com.kodraliu.localrock.shared.protocol.RoborockProtocolException> {
            parsePatrolThumbnails(data.copyOfRange(0, data.size - 4))
        }
    }
}
