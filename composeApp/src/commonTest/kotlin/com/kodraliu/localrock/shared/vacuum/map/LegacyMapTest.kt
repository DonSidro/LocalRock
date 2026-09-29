package com.kodraliu.localrock.shared.vacuum.map

import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class LegacyMapTest {

    @OptIn(ExperimentalEncodingApi::class)
    private val raw: ByteArray by lazy { Base64.decode(Fixtures.LEGACY_MAP_SAMPLE_B64) }

    @Test
    fun parses_real_map_sample() {
        val map = parseLegacyMap(raw)
        // Self-pinned against the captured real_map_sample.bin (480239 bytes).
        assertEquals(439, map.width)
        assertEquals(516, map.height)
        assertEquals(439 * 516, map.grid.size)
        assertEquals(315, map.pixelOffsetLeft)
        assertEquals(142, map.pixelOffsetTop)
        assertEquals(0.05f, map.resolution)
    }

    @Test
    fun grid_translation_matches_format_spec() {
        val map = parseLegacyMap(raw)
        var outside = 0; var wall = 0; var floor = 0
        for (b in map.grid) when (b.toInt() and 0xff) {
            0 -> outside++
            127 -> wall++
            128 -> floor++
        }
        // From the captured fixture: 170284 outside / 4580 wall (low3==1) / 51660 floor (other).
        assertEquals(170284, outside)
        assertEquals(4580, wall)
        assertEquals(51660, floor)
    }

    @Test
    fun extracts_charger_and_robot_positions() {
        val map = parseLegacyMap(raw)
        val charger = map.chargerMm
        assertNotNull(charger)
        assertEquals(25552, charger.x)
        assertEquals(24972, charger.y)
        assertEquals(90, charger.angle)
        val robot = map.robotMm
        assertNotNull(robot)
        assertEquals(25553, robot.x)
        assertEquals(25152, robot.y)
        assertEquals(90, robot.angle)
    }

    @Test
    fun extracts_cleaning_path() {
        val map = parseLegacyMap(raw)
        assertEquals(5307, map.pathMm.size)
        // First / last points from a direct python struct.unpack of the fixture's PATH block.
        val first = map.pathMm.first()
        assertEquals(25555, first.x)
        assertEquals(25206, first.y)
        val last = map.pathMm.last()
        assertEquals(25554, last.x)
        assertEquals(25156, last.y)
        // Path's final point should be within a few cm of the robot's reported position.
        val robot = map.robotMm!!
        val dxMm = (last.x - robot.x)
        val dyMm = (last.y - robot.y)
        val dist2 = dxMm * dxMm + dyMm * dyMm
        assertEquals(true, dist2 < 50 * 50, "Last path point is far from robot: dist²=$dist2 mm²")
    }

    @Test
    fun rejects_wrong_magic() {
        assertFailsWith<MapParseException> {
            parseLegacyMap(byteArrayOf(0xab.toByte(), 0xcd.toByte(), 20, 0, 0, 0, 0, 0))
        }
    }

    @Test
    fun real_sample_has_no_persistent_zones() {
        // The captured fixture has no no-go/no-mop zones set; the parser must not invent any.
        val map = parseLegacyMap(raw)
        assertEquals(emptyList(), map.noGoZones)
        assertEquals(emptyList(), map.noMopZones)
    }

    @Test
    fun parses_forbidden_zone_block() {
        val map = parseLegacyMap(syntheticMapWithForbiddenZone())
        assertEquals(1, map.noGoZones.size)
        assertEquals(emptyList(), map.noMopZones)
        val z = map.noGoZones.single()
        assertEquals(ZoneKind.NO_GO, z.kind)
        // Corners as written into the block, in mm.
        assertEquals(1000, z.x0); assertEquals(2000, z.y0)
        assertEquals(3000, z.x1); assertEquals(2000, z.y1)
        assertEquals(3000, z.x2); assertEquals(4000, z.y2)
        assertEquals(1000, z.x3); assertEquals(4000, z.y3)
        assertEquals(1000, z.minXmm); assertEquals(3000, z.maxXmm)
        assertEquals(2000, z.minYmm); assertEquals(4000, z.maxYmm)
    }

    /**
     * Minimal legacy map: 20-byte file header, a 2x2 IMAGE block (required by the parser), then a
     * FORBIDDEN_ZONES (type 9) block carrying one quad, laid out like the real maps below: a 12-byte
     * block header whose u16 at off+8 is the count, then the quad (8 x u16) right after the header.
     */
    private fun syntheticMapWithForbiddenZone(): ByteArray {
        val b = ByteArray(80)
        b[0] = 0x72; b[1] = 0x72          // 'rr'
        putU16(b, 2, 20)                   // file header length

        // IMAGE block at off=20
        putU16(b, 20, 2)                   // type = IMAGE
        putU16(b, 22, 28)                  // block header length
        putU32(b, 24, 4)                   // data length = 2*2 pixels
        // header body: imageTop@32, imageLeft@36, imageHeight@40, imageWidth@44 (ih = off+8 = 28)
        putU32(b, 40, 2)                   // height
        putU32(b, 44, 2)                   // width
        // pixels at off+28 = 48..52 left as 0 (all "outside")

        // FORBIDDEN_ZONES block at off=52
        putU16(b, 52, 9)                   // type
        putU16(b, 54, 12)                  // block header length
        putU32(b, 56, 16)                  // data length = 1 quad
        putU16(b, 60, 1)                   // zone count (at off+8)
        var p = 64                         // quad right after the header, off+12
        intArrayOf(1000, 2000, 3000, 2000, 3000, 4000, 1000, 4000).forEach {
            putU16(b, p, it); p += 2
        }
        return b
    }

    // ---- Hand-built blocks for cases the S8 captures don't contain ----

    @Test
    fun no_mop_block_is_parsed_like_no_go() {
        // Block 12 with one quad, same 12-byte header layout as the no-go block.
        val map = mapWithBlocks("0c000c001000000001000000" + quadHex(29750, 27400, 32400, 27400, 32400, 25950, 29750, 25950))
        assertEquals(
            listOf(MapZone(29750, 27400, 32400, 27400, 32400, 25950, 29750, 25950, ZoneKind.NO_MOP)),
            map.noMopZones,
        )
        assertEquals(emptyList(), map.noGoZones)
    }

    @Test
    fun unmodeled_restriction_blocks_are_detected_only_when_not_empty() {
        val map = mapWithBlocks(
            "1f000c005400000001000000" + "00".repeat(84),   // 31 smart door sill: one 84-byte entry
            "13000c000000000000000000",                      // 19 no-carpet areas: empty
            "17000c001000000001000000" + quadHex(1, 2, 3, 4, 5, 6, 7, 8), // 23 mop-only areas: one
        )
        assertEquals(setOf(23, 31), map.unmodeledRestrictionBlocks)
    }

    // ---- Blocks from a live S8 Pro Ultra map (2026-09-28), right after the official app saved
    // two walls, a no-go zone, a carpet area, a threshold and floor types. Only these blocks are
    // copied, byte for byte, into a minimal map; the house floor plan is not part of the fixture.
    // Each expected value is the exact payload the official app sent (decompiled_mqtt.jsonl).

    private val liveS8Blocks = listOf(
        "09000c001000000001000000" + "19769d5f877b9d5f877b1a5a19761a5a",          // no-go
        "0a000c001000000002000000" + "715fc249cc5616423d72ef5c48723657",          // walls
        "16000c001000000001000000" + "c47787603d7e87603d7e455ac477455a",          // carpet (22)
        "1c000c001000000001000000" + "5676e06749765464d7755564e475e167",          // threshold (28)
        "1f000c000000000000000000",                                                // smart sill (31), empty
        "1800080020000000" + "0000000000000000000000000000000003040003030404030304000000000000", // floor types
        "200008000f000000" + "105a00135a00145a00175a00185a00",                    // wood directions
    )

    private fun liveS8Map(): ParsedMap = mapWithBlocks(*liveS8Blocks.toTypedArray())

    /** Minimal legacy map (file header + 2x2 image) followed by the given blocks, as hex. */
    private fun mapWithBlocks(vararg blocksHex: String): ParsedMap {
        val header = ByteArray(20).also { b ->
            b[0] = 0x72; b[1] = 0x72
            putU16(b, 2, 20)
        }
        val image = ByteArray(28 + 4).also { b ->
            putU16(b, 0, 2); putU16(b, 2, 28); putU32(b, 4, 4)
            putU32(b, 20, 2); putU32(b, 24, 2)                   // height, width
        }
        val blocks = blocksHex.map { hexToBytes(it) }
        return parseLegacyMap(blocks.fold(header + image) { acc, b -> acc + b })
    }

    @Test
    fun live_s8_walls_and_no_go_match_what_the_official_app_saved() {
        val map = liveS8Map()
        // save_map {"data":[[1,24433,18882,22220,16918],[1,29245,23791,29256,22326],[0,30233,24477,...]]}
        assertEquals(listOf(VirtualWall(24433, 18882, 22220, 16918), VirtualWall(29245, 23791, 29256, 22326)), map.virtualWalls)
        assertEquals(
            listOf(MapZone(30233, 24477, 31623, 24477, 31623, 23066, 30233, 23066, ZoneKind.NO_GO)),
            map.noGoZones,
        )
    }

    @Test
    fun live_s8_carpet_and_threshold_blocks() {
        val map = liveS8Map()
        // set_carpet_area {"zone_data":[[30660,24711,32317,24711,32317,23109,30660,23109]]}
        assertEquals(
            listOf(MapZone(30660, 24711, 32317, 24711, 32317, 23109, 30660, 23109, ZoneKind.CARPET)),
            map.carpetAreas,
        )
        // app_set_smart_door_sill {"zones":[[2,30294,26592,30281,25684,30167,25685,30180,26593]]}
        assertEquals(
            listOf(MapZone(30294, 26592, 30281, 25684, 30167, 25685, 30180, 26593, ZoneKind.THRESHOLD)),
            map.thresholds,
        )
        assertEquals(emptySet(), map.unmodeledRestrictionBlocks)
    }

    @Test
    fun live_s8_floor_types() {
        val map = liveS8Map()
        // set_segment_ground_material {"data":[[21,4,-1],[24,3,90]]}
        assertEquals(FloorMaterial.TILE.id, map.segmentMaterials[21])
        assertEquals(FloorMaterial.WOOD.id, map.segmentMaterials[24])
        assertEquals(90, map.segmentMaterialDirections[24])
        // Kitchen (17) was already tile; the app's room sheet showed "Current: Tile".
        assertEquals(FloorMaterial.TILE.id, map.segmentMaterials[17])
        assertEquals(mapOf(16 to 90, 19 to 90, 20 to 90, 23 to 90, 24 to 90), map.segmentMaterialDirections)
    }

    /** Eight little-endian u16 corner values as hex. */
    // Blocks 22 and 39 from the S8 map after the official app saved a round carpet, then a
    // rectangular one (2026-09-29). Only carpet corners and flags, no floor plan.
    private val carpetBlock22 = "16000c002000000002000000" +
        "9064a068a867a068a867886590648865056154661d6454661d643c6305613c63"
    private val carpetBlock39 = "27000c002800000002000000" +
        "9064a068a867a068a867886590648865056154661d6454661d643c6305613c63" + "00e7330682e73302"

    @Test
    fun carpet_flags_come_from_block_39() {
        val map = mapWithBlocks(carpetBlock22, carpetBlock39)
        assertTrue(map.carpetFlagsPresent)
        assertEquals(listOf(0x0633E700L, 0x0233E782L), map.carpetAreas.map { it.flags })
        assertEquals(listOf(true, false), map.carpetAreas.map { it.isRoundCarpet })
        // Same corners as the set_carpet_area the official app sent for the round carpet.
        val round = map.carpetAreas[0]
        assertEquals(listOf(25744, 26784, 26536, 26784, 26536, 25992, 25744, 25992),
            listOf(round.x0, round.y0, round.x1, round.y1, round.x2, round.y2, round.x3, round.y3))
    }

    @Test
    fun carpets_without_block_39_have_no_flags() {
        val map = mapWithBlocks(carpetBlock22)
        assertFalse(map.carpetFlagsPresent)
        assertEquals(2, map.carpetAreas.size)
        assertTrue(map.carpetAreas.all { it.flags == null && !it.isRoundCarpet })
    }

    @Test
    fun block_39_that_disagrees_with_block_22_is_ignored() {
        val oneFlagged = "27000c001400000001000000" + "9064a068a867a068a867886590648865" + "00e73306"
        val map = mapWithBlocks(carpetBlock22, oneFlagged)
        assertFalse(map.carpetFlagsPresent)
        assertEquals(2, map.carpetAreas.size)
    }

    private fun quadHex(vararg v: Int): String =
        v.joinToString("") { n -> hexByte(n and 0xff) + hexByte((n ushr 8) and 0xff) }

    private fun hexByte(b: Int): String = b.toString(16).padStart(2, '0')

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
    }

    private fun putU32(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xff).toByte()
        b[off + 1] = ((v ushr 8) and 0xff).toByte()
        b[off + 2] = ((v ushr 16) and 0xff).toByte()
        b[off + 3] = ((v ushr 24) and 0xff).toByte()
    }
}
