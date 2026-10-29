package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapRoomLabelsTest {
    @Test fun adjacent_selected_rooms_keep_both_ordered_labels_and_tap_targets() {
        val labels = placeRoomLabels(listOf(
            RoomLabelRequest(3, Offset(160f, 120f), Size(110f, 30f), selectedOrder = 1),
            RoomLabelRequest(1, Offset(165f, 120f), Size(110f, 30f)),
            RoomLabelRequest(2, Offset(155f, 120f), Size(110f, 30f), selectedOrder = 0),
        ), Size(320f, 300f), 3f)
        assertEquals(listOf(2, 3), labels.take(2).map { it.id })
        assertFalse(labels[0].bounds.overlaps(labels[1].bounds))
        for (label in labels) assertEquals(label.id, labels.roomAtLabel(label.bounds.center))
    }

    @Test fun edge_labels_remain_inside_viewport_and_offscreen_rooms_stay_offscreen() {
        val labels = placeRoomLabels(listOf(
            RoomLabelRequest(1, Offset(2f, 2f), Size(110f, 30f), selectedOrder = 0),
            RoomLabelRequest(2, Offset(318f, 298f), Size(110f, 30f), selectedOrder = 1),
            RoomLabelRequest(3, Offset(-10f, 150f), Size(110f, 30f), selectedOrder = 2),
        ), Size(320f, 300f), 3f)
        assertEquals(listOf(1, 2), labels.map { it.id })
        assertTrue(labels.all { it.bounds.left >= 0 && it.bounds.top >= 0 && it.bounds.right <= 320 && it.bounds.bottom <= 300 })
    }

    @Test fun selecting_more_rooms_does_not_drop_previous_selections() {
        val requests = (0..7).map {
            RoomLabelRequest(it, Offset(160f + it, 150f), Size(105f, 28f), selectedOrder = it)
        }
        val labels = placeRoomLabels(requests, Size(320f, 400f), 3f)
        assertEquals(requests.map { it.id }, labels.map { it.id })
        for (i in labels.indices) for (j in 0 until i) assertFalse(labels[i].bounds.overlaps(labels[j].bounds))
    }
}
