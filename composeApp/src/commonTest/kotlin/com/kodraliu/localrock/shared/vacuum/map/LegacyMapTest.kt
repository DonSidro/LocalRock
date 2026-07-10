package com.kodraliu.localrock.shared.vacuum.map

import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
     * FORBIDDEN_ZONES (type 9) block carrying one quad. Layout matches [parseQuadZones]: u16 count
     * at off+8, quad of 8 u16 at off+12.
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
        putU16(b, 54, 8)                   // block header length
        putU32(b, 56, 20)                  // data length = count word + 1 quad
        putU16(b, 60, 1)                   // zone count (at off+8)
        var p = 64                         // quad at off+12
        intArrayOf(1000, 2000, 3000, 2000, 3000, 4000, 1000, 4000).forEach {
            putU16(b, p, it); p += 2
        }
        return b
    }

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
