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
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall

// Zone overlays are translucent, so one pair reads over both the light and dark palettes.
private val NO_GO_FILL = Color(0x33f44336)    // translucent red
private val NO_GO_STROKE = Color(0xccf44336)
private val NO_MOP_FILL = Color(0x332196f3)   // translucent blue
private val NO_MOP_STROKE = Color(0xcc2196f3)
private val WALL_STROKE = Color(0xffe53935)     // solid red, drawn as a line
private val CARPET_FILL = Color(0x40a1887f)     // translucent brown
private val CARPET_STROKE = Color(0xcca1887f)
private val THRESHOLD_FILL = Color(0x66ffb300)  // amber, small strips across doorways
private val THRESHOLD_STROKE = Color(0xffffb300)

/**
 * Follows whatever scheme is actually applied — including platform dynamic colour — rather than
 * the raw system setting, so the map never disagrees with the surface it sits on.
 */
@Composable
fun rememberMapBitmap(
    map: ParsedMap,
    drawRestrictions: Boolean = true,
    drawCleaningPath: Boolean = true,
    activeRunRoomIds: Set<Int> = emptySet(),
    selectedRoomIds: Set<Int> = emptySet(),
): ImageBitmap {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlightedRooms = highlightedRoomIds(selectedRoomIds, activeRunRoomIds)
    return remember(map, dark, drawRestrictions, drawCleaningPath, highlightedRooms) {
        renderMap(map, if (dark) DarkMapPalette else LightMapPalette, drawRestrictions, drawCleaningPath, highlightedRooms)
    }
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
            if (gv != 127 && gv != 128 && roomIdAtCell(x, y) == null) continue
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

/** [drawRestrictions] is false for the editor, which draws the shapes it is editing itself. */
private fun renderMap(
    map: ParsedMap, palette: MapPalette, drawRestrictions: Boolean,
    drawCleaningPath: Boolean, activeRunRoomIds: Set<Int>,
): ImageBitmap {
    val bitmap = ImageBitmap(map.width, map.height)
    val canvas = Canvas(bitmap)
    val wallPaint = Paint().apply { color = palette.wall }
    val floorPaint = Paint().apply { color = palette.floor }
    val roomPaint = Paint()
    val w = map.width
    val h = map.height
    val grid = map.grid
    for (y in 0 until h) {
        val rowOff = y * w
        // Flip Y so the map reads with origin at the top (matches the Roborock app).
        val drawY = (h - 1 - y).toFloat()
        for (x in 0 until w) {
            val gv = grid[rowOff + x].toInt() and 0xff
            val roomId = map.roomIdAtCell(x, y)
            val paint = when {
                gv == 127 -> wallPaint
                roomId != null -> {
                    val tint = palette.rooms[roomId % palette.rooms.size]
                    roomPaint.color = if (roomId in activeRunRoomIds) tint.active else tint.pale
                    roomPaint
                }
                gv == 128 -> floorPaint
                else -> continue
            }
            canvas.drawRect(
                Rect(left = x.toFloat(), top = drawY, right = (x + 1).toFloat(), bottom = drawY + 1f),
                paint,
            )
        }
    }
    val outline = Paint().apply { style = PaintingStyle.Stroke; strokeWidth = 0.45f }
    // Draw pale edges first, then target edges, so a neighbour cannot overwrite the emphasis.
    for (emphasized in listOf(false, true)) map.forEachRoomEdge { id, x0, y0, x1, y1 ->
        if ((id in activeRunRoomIds) == emphasized) {
            outline.color = if (emphasized) palette.rooms[id % palette.rooms.size].outline else palette.roomOutline
            outline.strokeWidth = if (emphasized) 0.7f else 0.35f
            canvas.drawLine(Offset(x0, y0), Offset(x1, y1), outline)
        }
    }
    if (drawCleaningPath && map.pathMm.size >= 2) drawPath(canvas, map, palette)
    if (drawRestrictions) {
        map.carpetAreas.forEach { drawZone(canvas, map, it, CARPET_FILL, CARPET_STROKE) }
        map.thresholds.forEach { drawZone(canvas, map, it, THRESHOLD_FILL, THRESHOLD_STROKE) }
        map.noMopZones.forEach { drawZone(canvas, map, it, NO_MOP_FILL, NO_MOP_STROKE) }
        map.noGoZones.forEach { drawZone(canvas, map, it, NO_GO_FILL, NO_GO_STROKE) }
        map.virtualWalls.forEach { drawWall(canvas, map, it) }
    }
    // Square dock, round robot: the two markers stay distinguishable without relying on colour.
    map.chargerMm?.let { drawMarker(canvas, map, it, palette.charger, palette.markerRing, square = true) }
    map.robotMm?.let { drawMarker(canvas, map, it, palette.robot, palette.markerRing, square = false) }
    return bitmap
}

private fun drawZone(canvas: Canvas, map: ParsedMap, zone: MapZone, fill: Color, stroke: Color) {
    val path = Path()
    if (zone.isRoundCarpet) {
        val a = map.mmToCell(zone.minXmm, zone.minYmm)
        val b = map.mmToCell(zone.maxXmm, zone.maxYmm)
        path.addOval(Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
    } else {
        val c0 = map.mmToCell(zone.x0, zone.y0)
        path.moveTo(c0.x, c0.y)
        val c1 = map.mmToCell(zone.x1, zone.y1); path.lineTo(c1.x, c1.y)
        val c2 = map.mmToCell(zone.x2, zone.y2); path.lineTo(c2.x, c2.y)
        val c3 = map.mmToCell(zone.x3, zone.y3); path.lineTo(c3.x, c3.y)
        path.close()
    }
    canvas.drawPath(path, Paint().apply { color = fill })
    canvas.drawPath(path, Paint().apply {
        color = stroke
        style = PaintingStyle.Stroke
        strokeWidth = 1f
    })
}

private fun drawWall(canvas: Canvas, map: ParsedMap, wall: VirtualWall) {
    val a = map.mmToCell(wall.x0, wall.y0)
    val b = map.mmToCell(wall.x1, wall.y1)
    canvas.drawLine(a, b, Paint().apply {
        color = WALL_STROKE
        strokeWidth = 1.5f
        strokeCap = StrokeCap.Round
    })
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
