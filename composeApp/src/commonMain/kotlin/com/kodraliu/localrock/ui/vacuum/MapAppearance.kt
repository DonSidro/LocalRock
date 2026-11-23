package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.graphics.Color
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

internal data class RoomMapColor(val pale: Color, val active: Color, val outline: Color)

/** Visual emphasis does not change the robot's reported cleaning targets. */
internal fun highlightedRoomIds(selected: Set<Int>, active: Set<Int>): Set<Int> = selected + active

/** Bounds-checked access to the shared floor-cell parser. */
internal fun ParsedMap.roomFloorIdAt(x: Int, y: Int): Int? =
    if (x in 0 until width && y in 0 until height) roomIdAtCell(y * width + x) else null

/** Edges of the actual grid cells; no smoothing, dilation or removal of small obstacles. */
internal inline fun ParsedMap.forEachRoomEdge(draw: (id: Int, x0: Float, y0: Float, x1: Float, y1: Float) -> Unit) {
    for (y in 0 until height) for (x in 0 until width) {
        val id = roomFloorIdAt(x, y) ?: continue
        val top = (height - 1 - y).toFloat()
        if (roomFloorIdAt(x - 1, y) != id) draw(id, x.toFloat(), top, x.toFloat(), top + 1)
        if (roomFloorIdAt(x + 1, y) != id) draw(id, x + 1f, top, x + 1f, top + 1)
        if (roomFloorIdAt(x, y + 1) != id) draw(id, x.toFloat(), top, x + 1f, top)
        if (roomFloorIdAt(x, y - 1) != id) draw(id, x.toFloat(), top + 1, x + 1f, top + 1)
    }
}
