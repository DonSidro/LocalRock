package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint

/**
 * The map is the signature surface, so it gets a palette per theme instead of one light bitmap
 * that glows in a dark UI. Room tints stay in a narrow luminance band so no single room shouts,
 * and markers differ in shape as well as colour so they don't rely on colour alone.
 */
private class MapPalette(
    val wall: Color,
    val floor: Color,
    val rooms: List<Color>,
    val path: Color,
    val charger: Color,
    val robot: Color,
    /** Drawn behind both markers so they stay readable on any room tint. */
    val markerRing: Color,
)

// Light is NOT the dark palette inverted: the rooms have to sit *above* their container, so the
// hero behind them is near-white and these tints carry enough weight to advance against it.
private val LightPalette = MapPalette(
    // A softer slate than near-black: at 1px over pastel, near-black reads as harsh scratchy noise.
    wall = Color(0xFF3A4A52),
    floor = Color(0xFFE9EFEE),
    rooms = listOf(
        Color(0xFFBFE0DC), Color(0xFFC8DCEF), Color(0xFFCBE3C4), Color(0xFFEBDCC4),
        Color(0xFFD8D2E8), Color(0xFFC9E2E4), Color(0xFFEBD5CC), Color(0xFFD3E0D0),
    ),
    // Deep enough to read as ink rather than neon against the pastels.
    path = Color(0xFFC75B00),
    charger = Color(0xFF1565C0),
    robot = Color(0xFF00595A),
    markerRing = Color(0xFFFFFFFF),
)

private val DarkPalette = MapPalette(
    wall = Color(0xFF070B0C),
    floor = Color(0xFF17201F),
    rooms = listOf(
        Color(0xFF1E3A38), Color(0xFF1F3140), Color(0xFF24382A), Color(0xFF3A3328),
        Color(0xFF2E2A3B), Color(0xFF1C3639), Color(0xFF38292A), Color(0xFF26332A),
    ),
    path = Color(0xFFFFD54F),
    charger = Color(0xFF64B5F6),
    robot = Color(0xFF4CDADA),
    markerRing = Color(0xFF0B1112),
)

/**
 * Follows whatever scheme is actually applied — including platform dynamic colour — rather than
 * the raw system setting, so the map never disagrees with the surface it sits on.
 */
@Composable
fun rememberMapBitmap(map: ParsedMap): ImageBitmap {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(map, dark) { renderMap(map, if (dark) DarkPalette else LightPalette) }
}

/**
 * Normalised bounds (0..1, in drawn-image space with Y already flipped) of the cells that actually
 * contain floor or wall. A parsed map is mostly empty grid, so a view that shows the whole bitmap
 * wastes most of its area on padding; this lets the hero frame the floor plan instead.
 */
class MapContentBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isUsable: Boolean get() = width > 0.01f && height > 0.01f
}

fun ParsedMap.contentBoundsNorm(): MapContentBounds {
    var minX = width
    var maxX = -1
    var minY = height
    var maxY = -1
    for (y in 0 until height) {
        val rowOff = y * width
        // Match renderMap's Y flip so the bounds are in the same space as the drawn bitmap.
        val drawY = height - 1 - y
        for (x in 0 until width) {
            val gv = grid[rowOff + x].toInt() and 0xff
            if (gv != 127 && gv != 128) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (drawY < minY) minY = drawY
            if (drawY > maxY) maxY = drawY
        }
    }
    if (maxX < 0 || maxY < 0) return MapContentBounds(0f, 0f, 1f, 1f)
    return MapContentBounds(
        left = minX.toFloat() / width,
        top = minY.toFloat() / height,
        right = (maxX + 1).toFloat() / width,
        bottom = (maxY + 1).toFloat() / height,
    )
}

private fun renderMap(map: ParsedMap, palette: MapPalette): ImageBitmap {
    val bitmap = ImageBitmap(map.width, map.height)
    val canvas = Canvas(bitmap)
    val wallPaint = Paint().apply { color = palette.wall }
    val floorPaint = Paint().apply { color = palette.floor }
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
                        roomPaint.color = palette.rooms[roomId % palette.rooms.size]
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
    if (map.pathMm.size >= 2) drawPath(canvas, map, palette)
    // Square dock, round robot: the two markers stay distinguishable without relying on colour.
    map.chargerMm?.let { drawMarker(canvas, map, it, palette.charger, palette.markerRing, square = true) }
    map.robotMm?.let { drawMarker(canvas, map, it, palette.robot, palette.markerRing, square = false) }
    return bitmap
}

private fun drawPath(canvas: Canvas, map: ParsedMap, palette: MapPalette) {
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
        color = palette.path
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
    ring: Color,
    square: Boolean,
    radius: Float = 4f,
) {

    val cellX = (point.x / 50f) - map.pixelOffsetLeft
    val cellYBottom = (point.y / 50f) - map.pixelOffsetTop
    // Flip Y to match the rendered orientation above.
    val cellY = (map.height - 1) - cellYBottom
    if (cellX !in 0f..map.width.toFloat() || cellY !in 0f..map.height.toFloat()) return
    val ringPaint = Paint().apply { this.color = ring }
    val fillPaint = Paint().apply { this.color = color }
    if (square) {
        canvas.drawRect(
            Rect(cellX - radius - 1f, cellY - radius - 1f, cellX + radius + 1f, cellY + radius + 1f),
            ringPaint,
        )
        canvas.drawRect(Rect(cellX - radius, cellY - radius, cellX + radius, cellY + radius), fillPaint)
    } else {
        canvas.drawCircle(Offset(cellX, cellY), radius + 1.5f, ringPaint)
        canvas.drawCircle(Offset(cellX, cellY), radius, fillPaint)
    }
}
