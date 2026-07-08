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
}
