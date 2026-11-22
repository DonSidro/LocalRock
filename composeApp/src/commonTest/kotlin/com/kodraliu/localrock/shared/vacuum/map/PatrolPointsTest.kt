package com.kodraliu.localrock.shared.vacuum.map

import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PatrolPointsTest {

    @OptIn(ExperimentalEncodingApi::class)
    private val sample: ByteArray by lazy { Base64.decode(Fixtures.LEGACY_MAP_SAMPLE_B64) }

    private fun le(v: Long, n: Int) = ByteArray(n) { (v shr (8 * it)).toByte() }

    // A 278-byte record as a Curv 2 Flow sent it: x, y, result, photo count, 8 ids of 32 bytes, 16 tail bytes.
    private fun record(x: Int, y: Int, result: Int, ids: List<String>): ByteArray {
        val r = ByteArray(278)
        le(x.toLong(), 2).copyInto(r, 0)
        le(y.toLong(), 2).copyInto(r, 2)
        r[4] = result.toByte()
        r[5] = ids.size.toByte()
        ids.forEachIndexed { j, id -> id.encodeToByteArray().copyInto(r, 6 + 32 * j) }
        return r
    }

    private fun block(type: Int, headerExtra: ByteArray, data: ByteArray) =
        le(type.toLong(), 2) + le((8 + headerExtra.size).toLong(), 2) + le(data.size.toLong(), 4) + headerExtra + data

    /** The sample map with a block inserted before its digest block. */
    private fun withBlock(extra: ByteArray): ByteArray {
        var off = 20
        while (true) {
            val type = (sample[off].toInt() and 0xff) or ((sample[off + 1].toInt() and 0xff) shl 8)
            if (type == 1024) break
            val h = (sample[off + 2].toInt() and 0xff) or ((sample[off + 3].toInt() and 0xff) shl 8)
            val d = (sample[off + 4].toInt() and 0xff) or ((sample[off + 5].toInt() and 0xff) shl 8) or
                ((sample[off + 6].toInt() and 0xff) shl 16) or ((sample[off + 7].toInt() and 0xff) shl 24)
            off += h + d
        }
        return sample.copyOfRange(0, off) + extra + sample.copyOfRange(off, sample.size)
    }

    private fun patrolBlock(task: Long, vararg points: ByteArray) =
        block(37, le(task, 4) + le(points.size.toLong(), 4), points.reduce { a, b -> a + b })

    @Test
    fun a_cruise_record_lists_its_points_and_photo_ids_in_order() {
        val task = 1794607499L
        val ids = { p: Int -> List(4) { n -> "P.$task.$p.$n" } }
        val map = parseLegacyMap(
            withBlock(
                patrolBlock(
                    task,
                    record(29950, 25150, 1, ids(0)),
                    record(29900, 36050, 1, ids(1)),
                    record(29300, 36000, 2, ids(2)),
                ),
            ),
        )
        assertEquals(task, map.patrolTaskId)
        assertEquals(
            listOf(
                PatrolPoint(29950, 25150, 1, ids(0)),
                PatrolPoint(29900, 36050, 1, ids(1)),
                PatrolPoint(29300, 36000, 2, ids(2)),
            ),
            map.patrolPoints,
        )
    }

    @Test
    fun an_empty_block_and_a_map_without_one_give_no_points() {
        assertEquals(emptyList(), parseLegacyMap(withBlock(block(37, le(5, 4) + le(0, 4), ByteArray(0)))).patrolPoints)
        val plain = parseLegacyMap(sample)
        assertEquals(emptyList(), plain.patrolPoints)
        assertNull(plain.patrolTaskId)
    }

    @Test
    fun a_point_keeps_only_as_many_ids_as_it_has_photos() {
        val map = parseLegacyMap(withBlock(patrolBlock(7, record(10, 20, 1, listOf("P.7.0.0", "P.7.0.1")))))
        assertEquals(listOf("P.7.0.0", "P.7.0.1"), map.patrolPoints.single().photoIds)
    }
}
