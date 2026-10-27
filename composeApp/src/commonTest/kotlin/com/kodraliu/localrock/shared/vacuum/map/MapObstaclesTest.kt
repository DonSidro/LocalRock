package com.kodraliu.localrock.shared.vacuum.map

import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapObstaclesTest {

    @OptIn(ExperimentalEncodingApi::class)
    private val raw: ByteArray by lazy { Base64.decode(Fixtures.LEGACY_MAP_SAMPLE_B64) }

    @Test
    fun the_s8_map_lists_its_obstacles() {
        // Block 15 of the captured S8 Pro Ultra map: nine 28-byte records, none with a photo id.
        val obstacles = parseLegacyMap(raw).obstacles
        assertEquals(9, obstacles.size)
        assertEquals(MapObstacle(x = 29023, y = 22194, type = 18, confidence = 0f, photoId = null, ignored = false), obstacles[0])
        assertNull(obstacles[0].typeName)
        val pedestal = obstacles[5]
        assertEquals("Pedestal", pedestal.typeName)
        assertEquals(0.85f, pedestal.confidence!!, 0.01f)
        assertTrue(obstacles.all { it.photoId == null && !it.ignored })
    }

    @Test
    fun a_36_byte_record_carries_its_photo_id() {
        // The Saros 20 Neo layout from #36: the photo id is zero padded ASCII from byte 12.
        val withPhoto = record(36, x = 1200, y = 3400, type = 2, photoId = "002OP8DjMLKGBRnX")
        val withoutPhoto = record(36, x = 500, y = 600, type = 69, photoId = null)
        val obstacles = parseObstacles(withPhoto + withoutPhoto, body = 0, count = 2, dataLen = 72, ignored = true)
        assertEquals(
            listOf(
                MapObstacle(1200, 3400, 2, 0.92f, "002OP8DjMLKGBRnX", ignored = true),
                MapObstacle(500, 600, 69, 0.92f, null, ignored = true),
            ),
            obstacles,
        )
        assertEquals("Shoes", obstacles[0].typeName)
    }

    @Test
    fun short_records_keep_what_they_have() {
        val obstacles = parseObstacles(record(4, x = 7, y = 8, type = 0, photoId = null), 0, 1, 4, ignored = false)
        assertEquals(listOf(MapObstacle(7, 8, null, null, null, ignored = false)), obstacles)
        assertEquals(emptyList(), parseObstacles(ByteArray(0), 0, 0, 0, ignored = false))
    }

    private fun record(size: Int, x: Int, y: Int, type: Int, photoId: String?): ByteArray {
        val r = ByteArray(size)
        fun put(off: Int, v: Int) {
            if (off + 1 < size) { r[off] = v.toByte(); r[off + 1] = (v shr 8).toByte() }
        }
        put(0, x); put(2, y); put(4, type); put(6, 9200); put(8, 1000)
        photoId?.encodeToByteArray()?.copyInto(r, 12)
        return r
    }
}
