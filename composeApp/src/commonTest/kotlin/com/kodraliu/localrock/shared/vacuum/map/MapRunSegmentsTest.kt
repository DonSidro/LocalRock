package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapRunSegmentsTest {
    @Test
    fun parses_the_entire_reported_list_and_map_revision() {
        val map = parseLegacyMap(mapWith(block(byteArrayOf(1, 3))))
        assertEquals(setOf(1, 3), map.reportedCleanSegments)
        assertEquals(LegacyMapRevision(0xf1234567L, 8), map.legacyRevision)
    }

    @Test
    fun missing_and_empty_are_distinct() {
        assertNull(parseLegacyMap(mapWith()).reportedCleanSegments)
        assertEquals(emptySet(), parseLegacyMap(mapWith(block(byteArrayOf()))).reportedCleanSegments)
    }

    @Test
    fun refuses_truncated_counts_unsupported_headers_duplicates_and_invalid_ids() {
        listOf(
            block(byteArrayOf(1), count = 2), block(byteArrayOf(1), count = 0x10001),
            block(byteArrayOf(1), count = -1), block(byteArrayOf(1), headerLen = 8),
            block(byteArrayOf(1), headerLen = 16), block(byteArrayOf(0)),
            block(byteArrayOf(32)), block(byteArrayOf(1, 1)),
        ).forEach { assertNull(parseLegacyMap(mapWith(it)).reportedCleanSegments) }
    }

    private fun block(body: ByteArray, count: Int = body.size, headerLen: Int = 12) = ByteArray(headerLen).also {
        it[0] = 11; it[2] = headerLen.toByte(); put32(it, 4, body.size)
        if (headerLen >= 12) put32(it, 8, count)
    } + body

    private fun mapWith(vararg blocks: ByteArray): ByteArray {
        val header = ByteArray(20).also {
            it[0] = 0x72; it[1] = 0x72; it[2] = 20; put32(it, 12, 0xf1234567.toInt()); put32(it, 16, 8)
        }
        val image = ByteArray(32).also {
            it[0] = 2; it[2] = 28; put32(it, 4, 4); put32(it, 20, 2); put32(it, 24, 2)
        }
        return blocks.fold(header + image) { a, b -> a + b }
    }
    private fun put32(b: ByteArray, off: Int, n: Int) { for (i in 0..3) b[off + i] = (n ushr (i * 8)).toByte() }
}
