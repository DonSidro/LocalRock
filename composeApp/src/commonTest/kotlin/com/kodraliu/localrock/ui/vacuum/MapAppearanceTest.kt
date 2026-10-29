package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapAppearanceTest {
    private fun legacy(raw: ByteArray) = ParsedMap(
        raw.size, 1,
        raw.map { if (it.toInt() and 7 == 1) 127.toByte() else if (it.toInt() and 7 == 7) 128.toByte() else 0.toByte() }.toByteArray(),
        emptyList(), 0.05f, originalGrid = raw,
    )

    @Test
    fun room_ids_in_unscanned_cells_and_walls_never_become_floor() {
        val map = legacy(byteArrayOf(15, 16, 25, 7))
        assertEquals(1, map.roomIdAtCell(0, 0))
        assertNull(map.roomIdAtCell(1, 0))
        assertNull(map.roomIdAtCell(2, 0))
        assertNull(map.roomIdAtCell(3, 0))
        assertNull(map.roomIdAtCell(-1, 0))
        val before = map.grid.copyOf()
        var edges = 0
        map.forEachRoomEdge { _, _, _, _, _ -> edges++ }
        assertEquals(4, edges)
        assertTrue(before.contentEquals(map.grid))
        assertEquals(1, map.wallCells)
    }

    @Test
    fun outlines_follow_room_boundaries_and_preserve_an_internal_obstacle() {
        val map = legacy(byteArrayOf(15, 15, 1, 15, 23))
        val edges = mutableListOf<List<Float>>()
        map.forEachRoomEdge { id, x0, y0, x1, y1 -> edges += listOf(id.toFloat(), x0, y0, x1, y1) }
        assertTrue(listOf(1f, 2f, 0f, 2f, 1f) in edges) // left edge of the real wall
        assertTrue(listOf(1f, 3f, 0f, 3f, 1f) in edges) // right edge of the real wall
        assertTrue(listOf(1f, 4f, 0f, 4f, 1f) in edges) // room boundary
        assertTrue(listOf(2f, 4f, 0f, 4f, 1f) in edges)
    }

    @Test
    fun b01_segment_floor_remains_visible_in_content_bounds() {
        val map = ParsedMap(4, 1, byteArrayOf(0, 2, 2, 0), emptyList(), 0.05f)
        assertEquals(2, map.roomIdAtCell(1, 0))
        val bounds = map.contentBoundsNorm()
        assertEquals(0.25f, bounds.left)
        assertEquals(0.75f, bounds.right)
    }

    @Test
    fun light_targets_have_deeper_fill_and_darker_outlines() {
        LightMapPalette.rooms.forEach {
            assertTrue(it.active.luminance() < it.pale.luminance())
            assertTrue(it.outline.luminance() < it.active.luminance())
        }
    }

    @Test
    fun selection_is_highlighted_before_a_run_without_changing_reported_targets() {
        val reported = setOf(4)
        assertEquals(setOf(1, 2), highlightedRoomIds(setOf(1, 2), emptySet()))
        assertEquals(setOf(1, 2, 4), highlightedRoomIds(setOf(1, 2), reported))
        assertEquals(setOf(4), reported)
        assertEquals(emptySet(), highlightedRoomIds(emptySet(), emptySet()))
        DarkMapPalette.rooms.forEach { assertTrue(it.active.luminance() < it.pale.luminance()) }
    }

    @Test
    fun unknown_room_type_gets_no_guessed_icon() {
        assertNull(mapRoomIcon(null))
        assertNull(mapRoomIcon(999))
        assertTrue(mapRoomIcon(1) != null)
    }

    @Test
    fun segment_changes_refresh_colours_without_resetting_the_viewport() {
        val first = legacy(byteArrayOf(15, 23))
        val updated = first.copy(originalGrid = byteArrayOf(23, 15),
            rooms = listOf(ParsedMapRoom(1, "Room", roomTypeTag = 1)), reportedCleanSegments = setOf(1))
        assertNotEquals(first, updated)
        val viewport = LiveMapViewport()
        val size = Size(320f, 500f)
        viewport.frameIfNeeded(first.viewportIdentity("robot/floor"), size, 2f to Offset.Zero)
        viewport.scale = 4f; viewport.offset = Offset(-60f, -80f)
        viewport.frameIfNeeded(updated.viewportIdentity("robot/floor"), size, 3f to Offset(10f, 20f))
        assertEquals(4f, viewport.scale)
        assertEquals(Offset(-60f, -80f), viewport.offset)
    }
}
