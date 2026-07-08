package com.kodraliu.localrock.shared.vacuum.map

import com.kodraliu.localrock.shared.protocol.gunzip
import com.kodraliu.localrock.shared.testing.Fixtures
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class B01MapTest {

    @OptIn(ExperimentalEncodingApi::class)
    private val raw: ByteArray by lazy {
        gunzip(Base64.decode(Fixtures.B01_MAP_SAMPLE_GZ_B64))
    }

    @Test
    fun parses_python_roborock_fixture() {
        val map = parseB01Map(raw)
        // Self-pinned against python-roborock's tests/map/testdata sample. Any drift means
        // a parser regression or the source fixture changed; investigate before updating.
        assertEquals(340, map.width)
        assertEquals(300, map.height)
        assertEquals(340 * 300, map.grid.size)
        assertEquals(3784, map.floorCells)
        assertEquals(25841, map.wallCells)
        assertEquals(10, map.rooms.size)
        assertEquals(0.05f, map.resolution)
        assertNotNull(map.rooms)
    }

    @Test
    fun invalid_bytes_throw() {
        assertFailsWith<MapParseException> {
            parseB01Map(byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte()))
        }
    }

    @Test
    fun grid_byte_values_are_only_0_127_128_in_fixture() {
        val map = parseB01Map(raw)
        var unknown = 0; var wall = 0; var floor = 0; var other = 0
        for (b in map.grid) when (b.toInt() and 0xff) {
            0 -> unknown++
            127 -> wall++
            128 -> floor++
            else -> other++
        }
        // Per python-roborock's B01 grid mapping, only these three values are expected.
        assertEquals(0, other, "Unexpected grid values present in fixture")
        assertEquals(72375, unknown)
        assertEquals(25841, wall)
        assertEquals(3784, floor)
    }
}
