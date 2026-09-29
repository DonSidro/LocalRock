package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.thresholdStrip
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapGeometryTest {

    // Offsets from the captured real map sample (LegacyMapTest): 439 x 516 cells, left 315, top 142.
    private val map = ParsedMap(
        width = 439, height = 516, grid = ByteArray(439 * 516), rooms = emptyList(), resolution = 0.05f,
        pixelOffsetLeft = 315, pixelOffsetTop = 142, format = MapFormat.LEGACY,
    )

    @Test
    fun mm_to_cell_matches_the_renderer_transform() {
        // Charger of the real sample: (25552, 24972) mm.
        val c = map.mmToCell(25552, 24972)
        assertEquals(25552 / 50f - 315, c.x)
        assertEquals((516 - 1) - (24972 / 50f - 142), c.y)
    }

    @Test
    fun cell_and_mm_round_trip() {
        for ((x, y) in listOf(25552 to 24972, 15750 to 7100, 37700 to 32900)) {
            assertEquals(x to y, map.cellToMm(map.mmToCell(x, y)))
        }
    }

    @Test
    fun canvas_and_mm_round_trip_at_any_draw_size() {
        val w = 1080f
        val h = w * 516 / 439
        val p = map.mmToCanvas(25552, 24972, w, h)
        assertEquals(25552 to 24972, map.canvasToMm(p, w, h))
    }

    @Test
    fun screen_top_is_robot_north() {
        // Y is flipped: a larger robot Y is higher up the screen (smaller cell Y).
        assertTrue(map.mmToCell(20000, 25000).y < map.mmToCell(20000, 20000).y)
    }

    @Test
    fun rotated_zone_hit_test() {
        // A diamond centred on (21000, 21000).
        val diamond = MapZone(21000, 22000, 22000, 21000, 21000, 20000, 20000, 21000, ZoneKind.NO_GO)
        assertTrue(diamond.contains(21000, 21000))
        // Inside the bounding box but outside the diamond.
        assertFalse(diamond.contains(20100, 20100))
        assertFalse(diamond.contains(23000, 21000))
    }

    @Test
    fun distance_to_wall() {
        assertEquals(100f, distanceToSegmentMm(1500, 1100, 1000, 1000, 2000, 1000))
        // Past the end, distance is to the endpoint.
        assertEquals(500f, distanceToSegmentMm(2300, 1400, 1000, 1000, 2000, 1000))
    }

    // A zone as the editor/official app writes it: TL, TR, BR, BL (Y up).
    private val rect = MapZone.rect(30000, 20000, 31000, 22000, ZoneKind.NO_GO)

    @Test
    fun move_shifts_every_corner() {
        val moved = rect.translated(150, -300)
        assertEquals(rect.corners().map { (x, y) -> (x + 150) to (y - 300) }, moved.corners())
    }

    @Test
    fun resize_keeps_the_opposite_corner_and_corner_order() {
        // Drag the top-right corner (index 1) out by 500 x 400; bottom-left (index 3) stays.
        val r = rect.resizedFromCorner(1, 31500, 22400, minSideMm = 200)
        assertEquals(MapZone.rect(30000, 20000, 31500, 22400, ZoneKind.NO_GO), r)
        assertEquals(rect.corners()[3], r.corners()[3])
    }

    @Test
    fun resize_never_flips_and_keeps_a_minimum_size() {
        // Drag the top-right corner past the bottom-left one.
        val r = rect.resizedFromCorner(1, 29000, 19000, minSideMm = 200)
        assertEquals(MapZone.rect(30000, 20000, 30200, 20200, ZoneKind.NO_GO), r)
    }

    @Test
    fun resizing_a_rotated_zone_keeps_its_angle() {
        val rotated = rect.rotated(PI / 6)
        val (x, y) = rotated.corners()[2]
        val r = rotated.resizedFromCorner(2, x + 300, y - 100, minSideMm = 200)
        fun angle(z: MapZone) = atan2((z.y1 - z.y0).toDouble(), (z.x1 - z.x0).toDouble())
        assertTrue(abs(angle(r) - angle(rotated)) < 0.01, "angle changed: ${angle(rotated)} -> ${angle(r)}")
        assertEquals(rotated.corners()[0], r.corners()[0])
    }

    @Test
    fun rotation_is_about_the_centre_and_round_trips() {
        val quarter = rect.rotated(PI / 2)
        val (cx, cy) = rect.center()
        val (qx, qy) = quarter.center()
        assertTrue(abs(cx - qx) <= 1 && abs(cy - qy) <= 1)
        // A 1000 x 2000 zone turned 90 degrees is 2000 wide.
        assertEquals(2000, quarter.maxXmm - quarter.minXmm)
        val back = quarter.rotated(-PI / 2)
        rect.corners().zip(back.corners()).forEach { (a, b) ->
            assertTrue(abs(a.first - b.first) <= 1 && abs(a.second - b.second) <= 1)
        }
    }

    @Test
    fun threshold_centre_line_round_trips_through_the_strip() {
        val strip = thresholdStrip(26202 to 17555, 27310 to 16599)
        val (a, b) = strip.thresholdCenterLine()
        assertTrue(abs(a.first - 26202) <= 1 && abs(a.second - 17555) <= 1)
        assertTrue(abs(b.first - 27310) <= 1 && abs(b.second - 16599) <= 1)
    }

    @Test
    fun wall_editing_and_map_bounds() {
        val wall = VirtualWall(1000, 1000, 2000, 1000)
        assertEquals(VirtualWall(1000, 1000, 2500, 1500), wall.withEnd(1, 2500, 1500))
        assertEquals(VirtualWall(1100, 900, 2100, 900), wall.translated(100, -100))
        assertFalse(wall.translated(-1001, 0).fitsMap())
        assertTrue(wall.fitsMap())
    }

    @Test
    fun room_names_are_validated_before_anything_is_sent() {
        assertEquals("Kitchen", validateRoomName("  Kitchen "))
        assertEquals("a".repeat(23), validateRoomName("a".repeat(23)))
        assertFailsWith<MapEditException> { validateRoomName("   ") }
        assertFailsWith<MapEditException> { validateRoomName("a".repeat(24)) }
        assertFailsWith<MapEditException> { validateRoomName("{json") }
        assertFailsWith<MapEditException> { validateRoomName("[x]") }
    }
}
