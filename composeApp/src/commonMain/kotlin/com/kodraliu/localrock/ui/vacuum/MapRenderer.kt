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
fun rememberMapBitmap(
    map: ParsedMap,
    drawRestrictions: Boolean = true,
    is3D: Boolean = false,
    isoQuadrant: Int = 0,
): ImageBitmap {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (dark) DarkPalette else LightPalette
    return remember(map, dark, drawRestrictions, is3D, isoQuadrant) {
        if (is3D) renderMapIsometric(map, palette, isoQuadrant) else renderMap(map, palette, drawRestrictions)
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

/** [drawRestrictions] is false for the editor, which draws the shapes it is editing itself. */
private fun renderMap(map: ParsedMap, palette: MapPalette, drawRestrictions: Boolean): ImageBitmap {
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

// Groups this many native grid cells into one isometric block per side, cutting draw calls roughly
// DOWNSAMPLE^2-fold. Native resolution (~50mm/cell) is finer than a dollhouse view needs anyway.
private const val DOWNSAMPLE = 2
private const val ISO_CELL_W = 3f
private const val ISO_CELL_H = 1.5f
// Tall relative to a block's footprint on purpose: the official app's dollhouse view exaggerates
// wall height well past real proportions so walls read as walls instead of thin borders.
private const val ISO_WALL_HEIGHT = 11f

private fun shade(c: Color, factor: Float) = Color(
    red = (c.red * factor).coerceIn(0f, 1f),
    green = (c.green * factor).coerceIn(0f, 1f),
    blue = (c.blue * factor).coerceIn(0f, 1f),
    alpha = c.alpha,
)

/**
 * A true isometric "dollhouse" render, matching the shape of the official app's 3D view: every
 * room is its own floor tile, every wall — interior walls included, not only the outer boundary —
 * is an extruded box with a top face and two shaded side faces, tiling seamlessly where wall runs
 * are adjacent. This is a separate bitmap built from scratch, not a tilt of the flat 2D one.
 *
 * [quadrant] is one of the 4 true isometric viewpoints (0-3, 90° apart). Unlike the flat 2D map,
 * rotating this view can't just spin the finished image: a wall's two visible side faces are
 * genuinely different faces from each corner, so a real viewpoint change has to re-run the whole
 * projection from rotated grid coordinates, which is what this does.
 */
private fun renderMapIsometric(map: ParsedMap, palette: MapPalette, quadrant: Int = 0): ImageBitmap {
    val w = map.width
    val h = map.height
    val grid = map.grid
    val raw = map.originalGrid

    var minX = w; var maxX = -1; var minY = h; var maxY = -1
    for (y in 0 until h) {
        val rowOff = y * w
        for (x in 0 until w) {
            val gv = grid[rowOff + x].toInt() and 0xff
            if (gv != 127 && gv != 128) continue
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return ImageBitmap(1, 1)

    var gw = (maxX - minX) / DOWNSAMPLE + 1
    var gh = (maxY - minY) / DOWNSAMPLE + 1

    // 0 empty, 1 floor, 2 wall, per downsampled block; a block with any wall cell counts as wall,
    // so a thin wall between two rooms is never lost to a floor-cell majority inside its own block.
    var kind = IntArray(gw * gh)
    var roomOf = IntArray(gw * gh) { -1 }
    for (cy in 0 until gh) {
        for (cx in 0 until gw) {
            var sawWall = false
            var sawFloor = false
            val roomCounts = HashMap<Int, Int>()
            for (dy in 0 until DOWNSAMPLE) {
                val y = minY + cy * DOWNSAMPLE + dy
                if (y > maxY) continue
                val rowOff = y * w
                for (dx in 0 until DOWNSAMPLE) {
                    val x = minX + cx * DOWNSAMPLE + dx
                    if (x > maxX) continue
                    when (grid[rowOff + x].toInt() and 0xff) {
                        127 -> sawWall = true
                        128 -> {
                            sawFloor = true
                            if (raw != null) {
                                val rid = (raw[rowOff + x].toInt() ushr 3) and 0x1f
                                roomCounts[rid] = (roomCounts[rid] ?: 0) + 1
                            }
                        }
                    }
                }
            }
            val idx = cy * gw + cx
            kind[idx] = if (sawWall) 2 else if (sawFloor) 1 else 0
            if (sawFloor) roomOf[idx] = roomCounts.maxByOrNull { it.value }?.key ?: -1
        }
    }

    // A real viewpoint change, not a spin of the finished picture: rotate the block grid itself 90°
    // clockwise per step, so the downstream code below — which always treats a cell's (cx+1,cy+1)
    // corner as the one nearest the camera — stays correct from every corner instead of needing to
    // know which corner is actually nearest for each of the 4 views.
    repeat(((quadrant % 4) + 4) % 4) {
        val rGw = gh; val rGh = gw
        val rKind = IntArray(rGw * rGh)
        val rRoomOf = IntArray(rGw * rGh) { -1 }
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                val nx = gh - 1 - y
                val ny = x
                val srcIdx = y * gw + x
                val dstIdx = ny * rGw + nx
                rKind[dstIdx] = kind[srcIdx]
                rRoomOf[dstIdx] = roomOf[srcIdx]
            }
        }
        gw = rGw; gh = rGh; kind = rKind; roomOf = rRoomOf
    }

    fun projX(cx: Float, cy: Float) = (cx - cy) * ISO_CELL_W
    fun projY(cx: Float, cy: Float, z: Float) = (cx + cy) * ISO_CELL_H - z

    var sMinX = Float.MAX_VALUE; var sMaxX = -Float.MAX_VALUE
    var sMinY = Float.MAX_VALUE; var sMaxY = -Float.MAX_VALUE
    for (cy in 0..gh) {
        for (cx in 0..gw) {
            val sx = projX(cx.toFloat(), cy.toFloat())
            if (sx < sMinX) sMinX = sx
            if (sx > sMaxX) sMaxX = sx
            for (z in floatArrayOf(0f, ISO_WALL_HEIGHT)) {
                val sy = projY(cx.toFloat(), cy.toFloat(), z)
                if (sy < sMinY) sMinY = sy
                if (sy > sMaxY) sMaxY = sy
            }
        }
    }
    val pad = 4f
    val bmW = (sMaxX - sMinX + pad * 2).toInt().coerceAtLeast(1)
    val bmH = (sMaxY - sMinY + pad * 2).toInt().coerceAtLeast(1)
    val offX = -sMinX + pad
    val offY = -sMinY + pad

    val bitmap = ImageBitmap(bmW, bmH)
    val canvas = Canvas(bitmap)

    fun pt(cx: Float, cy: Float, z: Float) = Offset(projX(cx, cy) + offX, projY(cx, cy, z) + offY)
    fun quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
        val path = Path().apply {
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        canvas.drawPath(path, Paint().apply { this.color = color })
    }

    // Back-to-front painter's order: (cx+cy) is exactly the screen-depth this projection sorts on.
    val order = (0 until gh).asSequence().flatMap { cy -> (0 until gw).asSequence().map { cx -> cx to cy } }
        .sortedBy { (cx, cy) -> cx + cy }
    for ((cx, cy) in order) {
        val idx = cy * gw + cx
        val fx = cx.toFloat(); val fy = cy.toFloat()
        when (kind[idx]) {
            1 -> {
                val color = roomOf[idx].takeIf { it >= 0 }?.let { palette.rooms[it % palette.rooms.size] } ?: palette.floor
                quad(pt(fx, fy, 0f), pt(fx + 1, fy, 0f), pt(fx + 1, fy + 1, 0f), pt(fx, fy + 1, 0f), color)
            }
            2 -> {
                val b00 = pt(fx, fy, 0f); val b10 = pt(fx + 1, fy, 0f)
                val b11 = pt(fx + 1, fy + 1, 0f); val b01 = pt(fx, fy + 1, 0f)
                val p00 = pt(fx, fy, ISO_WALL_HEIGHT); val p10 = pt(fx + 1, fy, ISO_WALL_HEIGHT)
                val p11 = pt(fx + 1, fy + 1, ISO_WALL_HEIGHT); val p01 = pt(fx, fy + 1, ISO_WALL_HEIGHT)
                // The two faces meeting at the cell's nearest (highest cx+cy) corner are the ones
                // the camera sees; adjoining wall cells share these edges, so runs tile seamlessly.
                quad(b10, b11, p11, p10, shade(palette.wall, 0.75f))
                quad(b01, b11, p11, p01, shade(palette.wall, 0.55f))
                quad(p00, p10, p11, p01, palette.wall)
            }
        }
    }

    fun projectMm(point: ParsedMapPoint): Pair<Float, Float>? {
        var gx = ((point.x / 50f) - map.pixelOffsetLeft - minX) / DOWNSAMPLE
        var gy = ((maxY - minY) - ((point.y / 50f) - map.pixelOffsetTop - minY)) / DOWNSAMPLE
        var pw = (maxX - minX) / DOWNSAMPLE + 1f
        var ph = (maxY - minY) / DOWNSAMPLE + 1f
        // Same per-step rotation the block grid above was built with, chained the same number of
        // times, so a marker lands on the rotated floor plan rather than the unrotated one.
        repeat(((quadrant % 4) + 4) % 4) {
            val nx = ph - gy; val ny = gx
            val nw = ph; val nh = pw
            gx = nx; gy = ny; pw = nw; ph = nh
        }
        if (gx !in 0f..gw.toFloat() || gy !in 0f..gh.toFloat()) return null
        return gx to gy
    }
    fun drawIsoMarker(point: ParsedMapPoint, color: Color, ring: Color, square: Boolean) {
        val (gx, gy) = projectMm(point) ?: return
        val center = pt(gx, gy, 2f)
        val r = 4f
        if (square) {
            canvas.drawRect(Rect(center.x - r - 1f, center.y - r - 1f, center.x + r + 1f, center.y + r + 1f), Paint().apply { this.color = ring })
            canvas.drawRect(Rect(center.x - r, center.y - r, center.x + r, center.y + r), Paint().apply { this.color = color })
        } else {
            canvas.drawCircle(center, r + 1.5f, Paint().apply { this.color = ring })
            canvas.drawCircle(center, r, Paint().apply { this.color = color })
        }
    }
    map.chargerMm?.let { drawIsoMarker(it, palette.charger, palette.markerRing, square = true) }
    map.robotMm?.let { drawIsoMarker(it, palette.robot, palette.markerRing, square = false) }

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
