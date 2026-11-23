package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanPoint
import com.kodraliu.localrock.shared.vacuum.map.FloorPlanZone
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloorPlanTest {
    private fun zone(segment: Int, vararg p: Triple<Int, Int, Int>) =
        FloorPlanZone(segment, p.map { (x, y, t) -> FloorPlanPoint(x, y, t) })

    private fun run(x0: Int, y0: Int, x1: Int, y1: Int) =
        FloorPlanRun(Offset(x0.toFloat(), y0.toFloat()), Offset(x1.toFloat(), y1.toFloat()))

    private fun shapeOf(height: Int, z: FloorPlanZone) = buildFloorPlan(listOf(z), FloorPlanFrame(height)).single()

    @Test
    fun fill_is_the_polygon_in_drawn_coordinates_with_y_flipped() {
        val shape = shapeOf(10, zone(4, Triple(2, 1, 1), Triple(6, 1, 1), Triple(6, 3, 1), Triple(2, 3, 1)))
        assertEquals(4, shape.segment)
        assertEquals(listOf(Offset(2f, 9f), Offset(6f, 9f), Offset(6f, 7f), Offset(2f, 7f)), shape.fill)
    }

    @Test
    fun a_rectangle_of_one_type_gives_four_runs_including_the_closing_edge() {
        val shape = shapeOf(2, zone(1, Triple(0, 0, 1), Triple(4, 0, 1), Triple(4, 2, 1), Triple(0, 2, 1)))
        assertEquals(listOf(run(0, 2, 4, 2), run(4, 2, 4, 0), run(4, 0, 0, 0), run(0, 0, 0, 2)), shape.outer)
        assertTrue(shape.inner.isEmpty() && shape.door.isEmpty())
    }

    @Test
    fun collinear_edges_merge_and_a_run_that_wraps_past_the_first_point_joins_up() {
        val z = zone(1, Triple(0, 1, 1), Triple(0, 0, 1), Triple(2, 0, 1), Triple(4, 0, 1), Triple(4, 2, 1), Triple(0, 2, 1))
        val shape = shapeOf(2, z)
        assertEquals(setOf(run(0, 2, 4, 2), run(4, 2, 4, 0), run(4, 0, 0, 0), run(0, 0, 0, 2)), shape.outer.toSet())
        assertEquals(4, shape.outer.size)
    }

    @Test
    fun an_l_shape_keeps_its_corners_and_merges_only_straight_neighbours() {
        val z = zone(
            3, Triple(0, 0, 1), Triple(4, 0, 1), Triple(4, 2, 1), Triple(3, 2, 1), Triple(2, 2, 1), Triple(2, 4, 1), Triple(0, 4, 1), Triple(0, 2, 1),
        )
        val shape = shapeOf(4, z)
        assertEquals(6, shape.outer.size)
        assertTrue(shape.outer.contains(run(4, 2, 2, 2)))
        assertTrue(shape.outer.contains(run(0, 0, 0, 4)))
    }

    @Test
    fun a_plus_shape_has_twelve_runs() {
        val plus = arrayOf(
            Triple(1, 0, 1), Triple(2, 0, 1), Triple(2, 1, 1), Triple(3, 1, 1), Triple(3, 2, 1), Triple(2, 2, 1),
            Triple(2, 3, 1), Triple(1, 3, 1), Triple(1, 2, 1), Triple(0, 2, 1), Triple(0, 1, 1), Triple(1, 1, 1),
        )
        assertEquals(12, shapeOf(3, zone(5, *plus)).outer.size)
    }

    @Test
    fun edges_are_grouped_by_the_type_of_their_start_point_and_other_types_are_dropped() {
        val shape = shapeOf(2, zone(2, Triple(0, 0, 1), Triple(4, 0, 2), Triple(4, 2, 3), Triple(0, 2, 5)))
        assertEquals(listOf(run(0, 2, 4, 2)), shape.outer)
        assertEquals(listOf(run(4, 2, 4, 0)), shape.inner)
        assertEquals(listOf(run(4, 0, 0, 0)), shape.door)
    }

    @Test
    fun type_4_edges_are_doors_too_type_6_is_an_inner_wall_and_type_5_is_not_drawn() {
        val shape = shapeOf(2, zone(7, Triple(0, 0, 4), Triple(4, 0, 5), Triple(4, 2, 6), Triple(0, 2, 1)))
        assertEquals(listOf(run(0, 2, 4, 2)), shape.door)
        assertEquals(listOf(run(0, 0, 0, 2)), shape.outer)
        assertEquals(listOf(run(4, 0, 0, 0)), shape.inner)
    }

    @Test
    fun a_door_in_the_middle_of_a_straight_side_splits_the_wall_around_it() {
        val shape = shapeOf(2, zone(6, Triple(0, 0, 1), Triple(2, 0, 3), Triple(4, 0, 1), Triple(4, 2, 1), Triple(0, 2, 1)))
        assertEquals(listOf(run(2, 2, 4, 2)), shape.door)
        assertEquals(run(0, 2, 2, 2), shape.outer.first())
        assertEquals(4, shape.outer.size)
    }

    @Test
    fun too_few_points_make_no_shape_and_a_repeated_point_makes_no_edge() {
        assertTrue(buildFloorPlan(listOf(zone(1, Triple(0, 0, 1), Triple(1, 0, 1))), FloorPlanFrame(2)).isEmpty())
        val dup = zone(1, Triple(0, 0, 1), Triple(0, 0, 1), Triple(4, 0, 1), Triple(4, 2, 1), Triple(0, 2, 1))
        assertEquals(4, shapeOf(2, dup).outer.size)
    }
}

class FloorPlanBoundsTest {
    private fun zone(vararg p: Pair<Int, Int>) = FloorPlanZone(1, p.map { FloorPlanPoint(it.first, it.second, 1) })

    @Test
    fun polygons_that_reach_past_the_cells_widen_the_bounds_and_the_stroke_is_added() {
        val cells = MapContentBounds(0.2f, 0.2f, 0.8f, 0.8f)
        val b = floorPlanBoundsNorm(cells, listOf(zone(10 to 10, 90 to 10, 90 to 90, 10 to 90)), 100, 100, 1f)
        assertEquals(0.095f, b.left, 1e-4f)
        assertEquals(0.905f, b.right, 1e-4f)
        assertEquals(0.095f, b.top, 1e-4f)
        assertEquals(0.905f, b.bottom, 1e-4f)
    }

    @Test
    fun cells_that_reach_past_the_polygons_keep_their_bounds_and_the_result_stays_in_the_image() {
        val cells = MapContentBounds(0.1f, 0.1f, 1f, 1f)
        val b = floorPlanBoundsNorm(cells, listOf(zone(0 to 50, 50 to 50, 50 to 100, 0 to 100)), 100, 100, 2f)
        assertEquals(0f, b.left)
        assertEquals(1f, b.right)
        assertEquals(0f, b.top)
        assertEquals(1f, b.bottom)
    }

    @Test
    fun no_cells_leaves_the_polygon_bounds_alone() {
        val b = floorPlanBoundsNorm(null, listOf(zone(20 to 20, 60 to 20, 60 to 40, 20 to 40)), 100, 100, 0f)
        assertEquals(0.2f, b.left, 1e-4f)
        assertEquals(0.6f, b.right, 1e-4f)
        assertEquals(0.6f, b.top, 1e-4f)
        assertEquals(0.8f, b.bottom, 1e-4f)
    }
}

class ErasedBoundsTest {
    private fun map(erase: Boolean) = ParsedMap(
        width = 10, height = 2, grid = ByteArray(20) { 128.toByte() }, rooms = emptyList(), resolution = 0.05f,
        pixelOffsetLeft = 2, pixelOffsetTop = 1, format = MapFormat.LEGACY,
        originalGrid = ByteArray(20) { i -> ((if (i % 10 < 5) 1 else 2) shl 3 or 7).toByte() },
        eraserZones = if (erase) listOf(MapZone.rect(100, 50, 350, 250, ZoneKind.ERASER)) else emptyList(),
        zoneLines = listOf(FloorPlanZone(1, listOf(FloorPlanPoint(0, 0, 1), FloorPlanPoint(5, 0, 1), FloorPlanPoint(5, 2, 1), FloorPlanPoint(0, 2, 1)))),
    )

    @Test
    fun an_erased_room_leaves_the_framing_bounds() {
        assertEquals(0f, map(false).contentBoundsNorm().left)
        assertEquals(0.5f, map(true).contentBoundsNorm().left, 1e-4f)
    }

    @Test
    fun the_raw_cell_view_frames_by_its_cells_alone() {
        val m = map(false).copy(zoneLines = listOf(FloorPlanZone(1, listOf(FloorPlanPoint(0, 0, 1), FloorPlanPoint(5, 0, 1), FloorPlanPoint(5, 2, 1)))))
        assertEquals(0f, m.contentBoundsNorm(floorPlan = false).left)
        assertEquals(1f, m.contentBoundsNorm(floorPlan = false).right)
    }
}

class FloorPlanColoursTest {
    private fun box(room: Int, x0: Int, y0: Int, x1: Int, y1: Int) =
        FloorPlanZone(room, listOf(FloorPlanPoint(x0, y0, 1), FloorPlanPoint(x1, y0, 1), FloorPlanPoint(x1, y1, 1), FloorPlanPoint(x0, y1, 1)))

    // Rooms never touch in the cells (a gap of zero cells between rows), so only the polygons can tell.
    private fun colours(rooms: Int, touches: Set<Pair<Int, Int>>, hidden: Set<Int> = emptySet()): Map<Int, Int> {
        val raw = ByteArray(rooms * 2) { i -> if (i % 2 == 0) (((i / 2 + 1) shl 3) or 7).toByte() else 0 }
        return appRoomColours(raw, rooms * 2, 1, touches, hidden)
    }

    @Test
    fun rooms_whose_polygons_share_a_wall_touch_and_corners_or_gaps_do_not() {
        val zones = listOf(box(1, 0, 0, 10, 10), box(2, 10, 0, 20, 10), box(3, 20, 10, 30, 20), box(4, 0, 12, 10, 20))
        assertEquals(setOf(1 to 2), floorPlanTouches(zones))
    }

    @Test
    fun a_chain_alternates_colours() {
        val c = colours(4, setOf(1 to 2, 2 to 3, 3 to 4))
        assertTrue((1..3).all { c[it] != c[it + 1] })
    }

    @Test
    fun a_hall_touching_everyone_gets_a_colour_none_of_its_neighbours_share() {
        val c = colours(6, (2..6).map { 1 to it }.toSet())
        assertTrue((2..6).all { c[it] != c[1] })
    }

    @Test
    fun a_fifth_mutual_neighbour_has_no_colour_left_and_falls_back_to_grey() {
        val all = (1..5).flatMap { a -> (a + 1..5).map { a to it } }.toSet()
        val c = colours(5, all)
        assertEquals(1, c.values.count { it == 0 })
        assertEquals(NO_COLOUR_SLOT, roomSlot(c.values.first { it == 0 }))
    }

    @Test
    fun an_erased_room_takes_no_colour() {
        val c = colours(3, setOf(1 to 2, 2 to 3), hidden = setOf(2))
        assertEquals(null, c[2])
    }

    private fun roomSlot(colour: Int) = if (colour in 1..4) colour - 1 else NO_COLOUR_SLOT
}

