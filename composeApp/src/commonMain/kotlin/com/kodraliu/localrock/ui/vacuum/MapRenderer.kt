package com.kodraliu.localrock.ui.vacuum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint

private val WALL_COLOR = Color(0xff263238)
private val FLOOR_COLOR = Color(0xffe0f2f1)
private val CHARGER_COLOR = Color(0xff2196f3)
private val ROBOT_COLOR = Color(0xfff44336)
private val PATH_COLOR = Color(0xffffd54f) // amber, visible over both floor + wall

// Palette for room coloring; index = room_id & 0x1f. Hand-picked pastels.
private val ROOM_PALETTE: List<Color> = listOf(
    Color(0xffb3e5fc), Color(0xffc8e6c9), Color(0xfffff9c4), Color(0xffffccbc),
    Color(0xfff8bbd0), Color(0xffd1c4e9), Color(0xffb2dfdb), Color(0xffd7ccc8),
    Color(0xffe1bee7), Color(0xffbbdefb), Color(0xffdcedc8), Color(0xfffff59d),
    Color(0xffffab91), Color(0xfff48fb1), Color(0xffb39ddb), Color(0xff80cbc4),
    Color(0xffbcaaa4), Color(0xffce93d8), Color(0xff90caf9), Color(0xffc5e1a5),
    Color(0xfffff176), Color(0xffff8a65), Color(0xfff06292), Color(0xff9575cd),
    Color(0xff4db6ac), Color(0xffa1887f), Color(0xffba68c8), Color(0xff64b5f6),
    Color(0xffaed581), Color(0xfffdd835), Color(0xffff7043), Color(0xffec407a),
)


@Composable
fun rememberMapBitmap(map: ParsedMap): ImageBitmap = remember(map) {
    val bitmap = ImageBitmap(map.width, map.height)
    val canvas = Canvas(bitmap)
    val wallPaint = Paint().apply { color = WALL_COLOR }
    val floorPaint = Paint().apply { color = FLOOR_COLOR }
    val roomPaint = Paint()
    val w = map.width
    val h = map.height
    val grid = map.grid
    val raw = map.originalGrid
    for (y in 0 until h) {
        val rowOff = y * w
        // Flip Y so the map reads with origin at the top (matches the Roborock app).
        val drawY = (h - 1 - y).toFloat()
        for (x in 0 until w) {
            val gv = grid[rowOff + x].toInt() and 0xff
            val paint = when (gv) {
                127 -> wallPaint
                128 -> {
                    if (raw != null) {
                        val roomId = (raw[rowOff + x].toInt() ushr 3) and 0x1f
                        roomPaint.color = ROOM_PALETTE[roomId % ROOM_PALETTE.size]
                        roomPaint
                    } else floorPaint
                }
                else -> continue
            }
            canvas.drawRect(
                Rect(left = x.toFloat(), top = drawY, right = (x + 1).toFloat(), bottom = drawY + 1f),
                paint,
            )
        }
    }
    if (map.pathMm.size >= 2) drawPath(canvas, map)
    map.chargerMm?.let { drawMarker(canvas, map, it, CHARGER_COLOR, radius = 4f) }
    map.robotMm?.let { drawMarker(canvas, map, it, ROBOT_COLOR, radius = 4f) }
    bitmap
}

private fun drawPath(canvas: Canvas, map: ParsedMap) {
    val gfxPath = Path()
    var started = false
    for (point in map.pathMm) {
        val cellX = (point.x / 50f) - map.pixelOffsetLeft
        val cellYBottom = (point.y / 50f) - map.pixelOffsetTop
        val cellY = (map.height - 1) - cellYBottom
        if (cellX !in 0f..map.width.toFloat() || cellY !in 0f..map.height.toFloat()) {
            started = false
            continue
        }
        if (!started) {
            gfxPath.moveTo(cellX, cellY)
            started = true
        } else {
            gfxPath.lineTo(cellX, cellY)
        }
    }
    val pathPaint = Paint().apply {
        color = PATH_COLOR
        style = PaintingStyle.Stroke
        strokeWidth = 1f
        strokeCap = StrokeCap.Round
    }
    canvas.drawPath(gfxPath, pathPaint)
}

private fun drawMarker(
    canvas: Canvas,
    map: ParsedMap,
    point: ParsedMapPoint,
    color: Color,
    radius: Float,
) {

    val cellX = (point.x / 50f) - map.pixelOffsetLeft
    val cellYBottom = (point.y / 50f) - map.pixelOffsetTop
    // Flip Y to match the rendered orientation above.
    val cellY = (map.height - 1) - cellYBottom
    if (cellX !in 0f..map.width.toFloat() || cellY !in 0f..map.height.toFloat()) return
    val paint = Paint().apply { this.color = color }
    canvas.drawCircle(Offset(cellX, cellY), radius, paint)
}
