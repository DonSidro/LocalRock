package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.graphics.Color
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

internal data class RoomMapColor(val pale: Color, val active: Color, val outline: Color)

/** Visual emphasis does not change the robot's reported cleaning targets. */
internal fun highlightedRoomIds(selected: Set<Int>, active: Set<Int>): Set<Int> = selected + active

internal class MapPalette(
    val wall: Color,
    val floor: Color,
    val rooms: List<RoomMapColor>,
    val roomOutline: Color,
    val path: Color,
    val charger: Color,
    val robot: Color,
    val markerRing: Color,
)

internal val LightMapPalette = MapPalette(
    wall = Color(0xFF9BAAAE), floor = Color(0xFFF0F3F2), roomOutline = Color(0xFFBDC8CA),
    rooms = listOf(
        RoomMapColor(Color(0xFFBFDCD6), Color(0xFF79B9AB), Color(0xFF426E65)),
        RoomMapColor(Color(0xFFBCD6F3), Color(0xFF7BAADF), Color(0xFF45678F)),
        RoomMapColor(Color(0xFFD0E3B5), Color(0xFF9BBF72), Color(0xFF58723F)),
        RoomMapColor(Color(0xFFF1DAA2), Color(0xFFD7B467), Color(0xFF89703C)),
        RoomMapColor(Color(0xFFD8C7EC), Color(0xFFAF91CF), Color(0xFF70558D)),
        RoomMapColor(Color(0xFFB7DDE8), Color(0xFF77BACD), Color(0xFF426F7C)),
        RoomMapColor(Color(0xFFF0C7B6), Color(0xFFD99B7E), Color(0xFF8C624E)),
        RoomMapColor(Color(0xFFDCE2B4), Color(0xFFB5C274), Color(0xFF6C7744)),
    ),
    path = Color(0xB3768D96), charger = Color(0xFF3975AD), robot = Color(0xFF216C70), markerRing = Color.White,
)

internal val DarkMapPalette = MapPalette(
    wall = Color(0xFF66777D), floor = Color(0xFF293638), roomOutline = Color(0xFF4D6168),
    rooms = listOf(
        RoomMapColor(Color(0xFF477D72), Color(0xFF2D594F), Color(0xFFAACFC5)),
        RoomMapColor(Color(0xFF4D719C), Color(0xFF345274), Color(0xFFB4CFEF)),
        RoomMapColor(Color(0xFF69824D), Color(0xFF485F32), Color(0xFFCBDDAF)),
        RoomMapColor(Color(0xFF958044), Color(0xFF6D5A2B), Color(0xFFEBD6A0)),
        RoomMapColor(Color(0xFF806697), Color(0xFF5D4675), Color(0xFFD6C3E9)),
        RoomMapColor(Color(0xFF467E8E), Color(0xFF2E5968), Color(0xFFACD7E3)),
        RoomMapColor(Color(0xFF9A705A), Color(0xFF704C39), Color(0xFFE9C1AD)),
        RoomMapColor(Color(0xFF7D874D), Color(0xFF586130), Color(0xFFD7DFAE)),
    ),
    path = Color(0xB3D5E3E5), charger = Color(0xFF82B5E0), robot = Color(0xFF80D0CD), markerRing = Color(0xFF172327),
)

/** Use only scanned room floor, never a room id in an empty cell or the robot's position. */
internal fun ParsedMap.roomIdAtCell(x: Int, y: Int): Int? {
    if (x !in 0 until width || y !in 0 until height) return null
    val index = y * width + x
    val raw = originalGrid
    if (raw != null) {
        val value = raw[index].toInt() and 255
        return if (value and 7 == 7) (value ushr 3).takeIf { it > 0 } else null
    }
    return (grid[index].toInt() and 255).takeIf { it in 1..126 }
}

/** Edges of the actual grid cells; no smoothing, dilation or removal of small obstacles. */
internal inline fun ParsedMap.forEachRoomEdge(draw: (id: Int, x0: Float, y0: Float, x1: Float, y1: Float) -> Unit) {
    for (y in 0 until height) for (x in 0 until width) {
        val id = roomIdAtCell(x, y) ?: continue
        val top = (height - 1 - y).toFloat()
        if (roomIdAtCell(x - 1, y) != id) draw(id, x.toFloat(), top, x.toFloat(), top + 1)
        if (roomIdAtCell(x + 1, y) != id) draw(id, x + 1f, top, x + 1f, top + 1)
        if (roomIdAtCell(x, y + 1) != id) draw(id, x.toFloat(), top, x + 1f, top)
        if (roomIdAtCell(x, y - 1) != id) draw(id, x.toFloat(), top + 1, x + 1f, top + 1)
    }
}
