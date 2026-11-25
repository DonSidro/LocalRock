package com.kodraliu.localrock.shared.vacuum.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Synthetic geometry only; no captured map or household coordinates are used. */
class MapFurnitureTest {
    @Test
    fun preserves_all_record_bytes_and_unsigned_corners() {
        val record = furnitureRecord().also { put16(it, 0, 60000) }
        val map = parseLegacyMap(mapWith(furnitureBlock(record)))
        val item = map.furniture.single()
        assertEquals(60000, item.cornersMm[0].x)
        assertEquals(247, item.type)
        assertEquals(9, item.subtype)
        assertEquals(1, item.displayFlag)
        assertEquals(record.map { it.toInt() and 255 }, item.rawBytes)
        assertEquals(listOf(16, 39), item.rawBytes.subList(16, 18))
        assertEquals(201, item.rawBytes[21])
        assertEquals(173, item.rawBytes[22])
    }

    @Test
    fun display_flag_filters_rendering_without_deleting_records() {
        val records = (0 until 13).map { furnitureRecord(flag = if (it < 11) 1 else 0) }
        val map = parseLegacyMap(mapWith(furnitureBlock(records.reduce { a, b -> a + b }, count = 13)))
        assertEquals(13, map.furniture.size)
        assertEquals(11, map.furniture.count { it.isVisible })
        assertTrue(map.furniture.takeLast(2).all { it.displayFlag == 0 })
        assertTrue(map.noGoZones.isEmpty())
        assertTrue(map.obstacles.isEmpty())
        assertTrue(map.unmodeledRestrictionBlocks.isEmpty())
    }

    @Test
    fun unknown_type_is_visible_but_unknown_display_flag_is_not_assumed() {
        val items = parseLegacyMap(mapWith(furnitureBlock(
            furnitureRecord(type = 255) + furnitureRecord(flag = 2), count = 2,
        ))).furniture
        assertTrue(items[0].isVisible)
        assertFalse(items[1].isVisible)
        assertEquals(2, items[1].displayFlag)
    }

    @Test
    fun missing_and_empty_furniture_are_valid_maps() {
        assertTrue(parseLegacyMap(mapWith()).furniture.isEmpty())
        val empty = parseLegacyMap(mapWith(furnitureBlock(byteArrayOf(), count = 0)))
        assertTrue(empty.furniture.isEmpty())
        assertEquals(1, empty.furnitureBlocks.size)
    }

    @Test
    fun count_mismatch_or_unknown_layout_is_retained_without_guessing_a_stride() {
        val cases = listOf(
            furnitureBlock(furnitureRecord(), count = 2),
            furnitureBlock(furnitureRecord(), count = 0),
            furnitureBlock(furnitureRecord(), count = 0x10001),
            furnitureBlock(furnitureRecord(), count = -1),
            furnitureBlock(furnitureRecord() + byteArrayOf(42), count = 1),
            furnitureBlock(furnitureRecord().copyOf(22)),
            furnitureBlock(furnitureRecord(), headerLen = 8),
            furnitureBlock(furnitureRecord(), headerLen = 16),
        )
        cases.forEach { block ->
            val map = parseLegacyMap(mapWith(block))
            assertTrue(map.furniture.isEmpty())
            val kept = map.furnitureBlocks.single()
            assertEquals(block.map { it.toInt() and 255 }, kept.headerBytes + kept.bodyBytes)
            assertEquals(4, map.grid.size)
        }
    }

    @Test
    fun truncated_and_overflowing_block_lengths_fail_safely() {
        val block = furnitureBlock(furnitureRecord())
        assertFailsWith<MapParseException> { parseLegacyMap(mapWith(block.copyOf(block.size - 1))) }
        assertFailsWith<MapParseException> {
            parseLegacyMap(mapWith(block.copyOf().also { put32(it, 4, Int.MAX_VALUE) }))
        }
        assertFailsWith<MapParseException> {
            parseLegacyMap(mapWith(block.copyOf().also { put32(it, 4, -1) }))
        }
        assertFailsWith<MapParseException> {
            parseLegacyMap(mapWith(block.copyOf().also { put16(it, 2, 7) }))
        }
    }

    @Test
    fun a_partial_record_does_not_consume_the_following_block() {
        val map = parseLegacyMap(mapWith(
            furnitureBlock(furnitureRecord().copyOf(22)), furnitureBlock(furnitureRecord()),
        ))
        assertEquals(2, map.furnitureBlocks.size)
        assertEquals(1, map.furniture.size)
    }

    @Test
    fun rotated_corners_keep_their_order_and_bad_geometry_is_not_drawn() {
        val rotated = furnitureRecord(corners = intArrayOf(500, 800, 800, 500, 500, 200, 200, 500))
        val crossed = furnitureRecord(corners = intArrayOf(100, 100, 200, 200, 100, 200, 200, 100))
        val collapsed = furnitureRecord(corners = IntArray(8) { 100 })
        val items = parseLegacyMap(mapWith(furnitureBlock(rotated + crossed + collapsed, count = 3))).furniture
        assertEquals(listOf(ParsedMapPoint(500, 800), ParsedMapPoint(800, 500), ParsedMapPoint(500, 200), ParsedMapPoint(200, 500)), items[0].cornersMm)
        assertTrue(items[0].isVisible)
        assertFalse(items[1].isVisible)
        assertFalse(items[2].isVisible)
        assertEquals(3, items.size)
    }

    @Test
    fun furniture_changes_participate_in_map_equality_for_live_refresh() {
        val first = parseLegacyMap(mapWith(furnitureBlock(furnitureRecord())))
        val same = parseLegacyMap(mapWith(furnitureBlock(furnitureRecord())))
        val changed = parseLegacyMap(mapWith(furnitureBlock(furnitureRecord(flag = 0))))
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, changed)
    }

    private fun furnitureRecord(
        flag: Int = 1, type: Int = 247,
        corners: IntArray = intArrayOf(100, 400, 500, 400, 500, 100, 100, 100),
    ): ByteArray = ByteArray(23).also { b ->
        corners.forEachIndexed { i, n -> put16(b, i * 2, n) }
        put16(b, 16, 10000)
        b[18] = type.toByte(); b[19] = 9; b[20] = flag.toByte(); b[21] = 201.toByte(); b[22] = 173.toByte()
    }

    private fun furnitureBlock(body: ByteArray, count: Int = 1, headerLen: Int = 12): ByteArray =
        ByteArray(headerLen).also { h ->
            put16(h, 0, 25); put16(h, 2, headerLen); put32(h, 4, body.size)
            if (headerLen >= 12) put32(h, 8, count)
        } + body

    private fun mapWith(vararg blocks: ByteArray): ByteArray {
        val header = ByteArray(20).also { it[0] = 0x72; it[1] = 0x72; put16(it, 2, 20) }
        val image = ByteArray(32).also {
            put16(it, 0, 2); put16(it, 2, 28); put32(it, 4, 4)
            put32(it, 20, 2); put32(it, 24, 2)
            for (i in 28..31) it[i] = 7
        }
        return blocks.fold(header + image) { a, b -> a + b }
    }

    private fun put16(b: ByteArray, off: Int, n: Int) { b[off] = n.toByte(); b[off + 1] = (n ushr 8).toByte() }
    private fun put32(b: ByteArray, off: Int, n: Int) { for (i in 0..3) b[off + i] = (n ushr (i * 8)).toByte() }
}
