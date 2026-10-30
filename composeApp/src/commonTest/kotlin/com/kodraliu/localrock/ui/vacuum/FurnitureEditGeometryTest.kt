package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import com.kodraliu.localrock.shared.vacuum.FurniturePlacement
import com.kodraliu.localrock.shared.vacuum.validFurniturePlacement
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FurnitureEditGeometryTest {
    private val bed = FurniturePlacement(1, 45, 1, listOf(
        ParsedMapPoint(10_000, 13_000), ParsedMapPoint(11_600, 13_000),
        ParsedMapPoint(11_600, 11_000), ParsedMapPoint(10_000, 11_000),
    ))

    @Test fun selection_padding_is_visual_and_handles_have_the_requested_corner_actions() {
        val footprint = listOf(Offset(100f, 100f), Offset(200f, 100f), Offset(200f, 250f), Offset(100f, 250f))
        val frame = furnitureSelectionCorners(footprint, gap = 24f, minSide = 96f)
        assertEquals(listOf(Offset(76f, 76f), Offset(224f, 76f), Offset(224f, 274f), Offset(76f, 274f)), frame)
        assertEquals(Offset(100f, 100f), footprint[0])
        assertEquals(FurnitureHandle.DELETE, furnitureHandleAt(frame[0], frame, 24f))
        assertEquals(FurnitureHandle.ROTATE, furnitureHandleAt(frame[1], frame, 24f))
        assertEquals(FurnitureHandle.MOVE, furnitureHandleAt(frame[3], frame, 24f))
        assertEquals(FurnitureHandle.RESIZE, furnitureHandleAt(frame[2], frame, 24f))
        assertNull(furnitureHandleAt(Offset(150f, 175f), frame, 24f))
    }

    @Test fun tiny_rotated_furniture_keeps_separate_touch_targets() {
        val footprint = listOf(Offset(50f, 50f), Offset(60f, 60f), Offset(40f, 80f), Offset(30f, 70f))
        val frame = furnitureSelectionCorners(footprint, gap = 24f, minSide = 96f)
        assertEquals(96f, (frame[1] - frame[0]).getDistance(), .001f)
        assertEquals(96f, (frame[3] - frame[0]).getDistance(), .001f)
        val before = footprint.reduce { a, b -> a + b } / 4f
        val after = frame.reduce { a, b -> a + b } / 4f
        assertTrue((after - before).getDistance() < .001f)
        FurnitureHandle.entries.forEach { handle ->
            assertEquals(handle, furnitureHandleAt(frame[handle.corner] + Offset(15f, 0f), frame, 24f))
        }
    }

    @Test fun grabbing_a_padded_handle_does_not_snap_the_saved_footprint_to_the_pointer() {
        assertEquals(bed, bed.resizedFromHandle(0, 0))
        assertEquals(bed, bed.rotatedFromHandle(12_000 to 14_000, 12_000 to 14_000))
        val resized = bed.resizedFromHandle(400, 0)
        assertEquals(2_000.0, resized.widthMm)
        assertEquals(bed.depthMm, resized.depthMm)
        assertEquals(bed.corners[0], resized.corners[0])
        assertTrue(validFurniturePlacement(resized))
    }

    @Test fun width_and_height_resize_independently_and_anchor_the_opposite_corner() {
        val resized = bed.resizedFromHandle(0, -600)
        assertEquals(bed.widthMm, resized.widthMm)
        assertEquals(2_600.0, resized.depthMm)
        assertEquals(bed.corners[0], resized.corners[0])
        val both = bed.resizedFromHandle(400, -600)
        assertEquals(2_000.0, both.widthMm)
        assertEquals(2_600.0, both.depthMm)
        assertEquals(bed.corners[0], both.corners[0])
        assertTrue(validFurniturePlacement(both))
    }

    @Test fun resizing_a_rotated_item_follows_its_local_axes() {
        val rotated = bed.rotated(45.0)
        val resized = rotated.resizedFromHandle(707, 707)
        assertEquals(rotated.widthMm + 1_000, resized.widthMm, 2.0)
        assertEquals(rotated.depthMm, resized.depthMm, 2.0)
        assertEquals(rotated.corners[0], resized.corners[0])
        assertEquals(rotated.angleDegrees, resized.angleDegrees, .1)
        assertTrue(validFurniturePlacement(resized))
    }

    @Test fun dragging_through_the_opposite_corner_never_inverts_or_collapses_an_item() {
        listOf(0.0, 15.0, 45.0, 90.0, 179.0).forEach { angle ->
            val item = bed.rotated(angle)
            val delta = item.corners[0].let { a -> item.corners[2].let { c ->
                (a.x - c.x) * 2 to (a.y - c.y) * 2
            } }
            val resized = item.resizedFromHandle(delta.first, delta.second)
            assertTrue(resized.widthMm >= 100 && resized.depthMm >= 100)
            assertEquals(item.corners[0], resized.corners[0])
            assertTrue(validFurniturePlacement(resized), "Invalid minimum size at $angle degrees")
        }
    }

    @Test fun rotation_tracks_clockwise_pointer_motion_in_robot_coordinates_without_changing_size() {
        val rotated = bed.rotatedFromHandle(11_800 to 12_000, 10_800 to 11_000)
        assertEquals(-90.0, rotated.angleDegrees, .001)
        assertEquals(bed.widthMm, rotated.widthMm, .001)
        assertEquals(bed.depthMm, rotated.depthMm, .001)
        assertEquals(bed.corners.sumOf { it.x }, rotated.corners.sumOf { it.x })
        assertEquals(bed.corners.sumOf { it.y }, rotated.corners.sumOf { it.y })
        assertTrue(validFurniturePlacement(rotated))
    }

    @Test fun drag_delta_accounts_for_map_origin_zoom_pan_and_screen_y_direction() {
        val map = ParsedMap(100, 80, ByteArray(8_000), emptyList(), .05f, pixelOffsetLeft = 10, pixelOffsetTop = 20)
        val scale = 3.25f
        val offset = Offset(57f, -93f)
        fun screen(x: Int, y: Int) = map.mmToCanvas(x, y, 500f, 400f) * scale + offset
        fun mm(point: Offset) = map.canvasToMm((point - offset) / scale, 500f, 400f)
        val start = screen(2_000, 3_000)
        val end = screen(2_400, 2_400)
        assertTrue(end.x > start.x && end.y > start.y)
        val a = mm(start); val b = mm(end)
        val resized = bed.resizedFromHandle(b.first - a.first, b.second - a.second)
        assertTrue(abs(resized.widthMm - 2_000) <= 1)
        assertTrue(abs(resized.depthMm - 2_600) <= 1)
    }
}
