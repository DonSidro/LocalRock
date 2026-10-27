package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapPoint
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.roomIdAtCell

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
// Blue, salmon, teal and yellow in the order the Roborock app numbers them, then grey for a room it has no colour for.
private val OfficialRoomColors = listOf(
    Color(0xFF6EA8EB), Color(0xFFDB7F66), Color(0xFF26CFC8), Color(0xFFE8BA5C), Color(0xFFB8B8B8),
)

private val LightPalette = MapPalette(
    // A softer slate than near-black: at 1px over pastel, near-black reads as harsh scratchy noise.
    wall = Color(0xFF3A4A52),
    floor = Color(0xFFE9EFEE),
    rooms = OfficialRoomColors,
    // Deep enough to read as ink rather than neon against the pastels.
    path = Color(0x99FFFFFF),
    charger = Color(0xFF1FB36A),
    robot = Color(0xFF1FB36A),
    markerRing = Color(0xFFFFFFFF),
)

private val DarkPalette = MapPalette(
    wall = Color(0xFF070B0C),
    floor = Color(0xFF17201F),
    rooms = OfficialRoomColors,
    path = Color(0x99FFFFFF),
    charger = Color(0xFF2ED47E),
    robot = Color(0xFF2ED47E),
    markerRing = Color(0xFF0B1112),
)

/**
 * Follows whatever scheme is actually applied — including platform dynamic colour — rather than
 * the raw system setting, so the map never disagrees with the surface it sits on.
 */
@Composable
fun rememberMapBitmap(map: ParsedMap, drawRestrictions: Boolean = true): ImageBitmap {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(map, dark, drawRestrictions) {
        renderMap(map, if (dark) DarkPalette else LightPalette, drawRestrictions)
    }
}

/**
 * How many times larger than the grid the detail screen draws its layers: as large as keeps the
 * biggest side under about 2400 pixels, and at most 3. Each step is 9x the memory, so a very large
 * map falls back to the grid's own size rather than risking running out.
 */
internal fun supersampleFor(map: ParsedMap): Int = (2400 / maxOf(map.width, map.height, 1)).coerceIn(1, 3)

/**
 * The map as (floor, overlay) at [scale] times the grid. The floor is the rooms and walls; the
 * overlay is the robot's path, zones and markers, drawn over a selected room's tint rather than
 * under it.
 */
@Composable
fun rememberMapLayers(map: ParsedMap, scale: Int): Pair<ImageBitmap, ImageBitmap> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (dark) DarkPalette else LightPalette
    // A live update replaces the map object every few seconds. The floor only depends on the grid and
    // rooms, so it is not redrawn for a robot that has merely moved; the overlay carries the route.
    val floor = remember(map.floorKey(), dark, scale) { renderLayer(map, palette, scale, overlay = false) }
    val overlay = remember(map, dark, scale) { renderLayer(map, palette, scale, overlay = true) }
    return floor to overlay
}

/**
 * What the floor, wall and selection layers are drawn from. Live updates give a new map object without
 * changing any of it, so these layers are keyed on this and not on the object.
 */
internal fun ParsedMap.floorKey(): List<Any?> = listOf(
    width, height, resolution, pixelOffsetLeft, pixelOffsetTop, format,
    grid.contentHashCode(), originalGrid?.contentHashCode(), rooms, segmentMaterials, segmentMaterialDirections,
)

/**
 * The map's wall cells alone, on a transparent bitmap at [scale] times the grid, as (side, top). The
 * 3D view stacks the side layer at rising heights and caps it with the lighter top, which extrudes
 * every wall — interior ones included — through whatever rotation the view is at, with no per-wall
 * geometry to rebuild.
 */
@Composable
fun rememberWallBitmaps(map: ParsedMap, scale: Int, enabled: Boolean): Pair<ImageBitmap, ImageBitmap>? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // Only the 3D view draws the walls, so the flat map does not pay for two more full-size bitmaps.
    if (!enabled) return null
    return remember(map.floorKey(), dark, scale) {
        val wall = (if (dark) DarkPalette else LightPalette).wall
        renderWalls(map, lerp(wall, Color.White, 0.10f), scale) to renderWalls(map, lerp(wall, Color.White, 0.30f), scale)
    }
}

/**
 * The same hue as [color] at full strength: how a room looks when it is picked. The map shows rooms
 * in muted tints so nothing shouts; a selected room keeps its own hue but comes up bright.
 */
internal fun vividOf(color: Color): Color {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta < 0.001f) return Color(0.78f, 0.78f, 0.78f)
    var hue = when (max) {
        r -> 60f * (((g - b) / delta) % 6f)
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    if (hue < 0f) hue += 360f
    return hsv(hue, saturation = 0.62f, value = 0.95f)
}

private fun hsv(hue: Float, saturation: Float, value: Float): Color {
    val c = value * saturation
    val sector = (hue / 60f) % 6f
    val x = c * (1f - kotlin.math.abs(sector % 2f - 1f))
    val m = value - c
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

/**
 * The selected rooms' floor cells at [scale] times the grid, each in a vivid version of that room's
 * own colour, on a transparent bitmap laid over the floor; null while nothing is selected. It
 * covers exactly the cells a tap on that room would pick.
 */
@Composable
fun rememberSelectionBitmap(map: ParsedMap, selectedRoomIds: List<Int>, scale: Int): ImageBitmap? {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(map.floorKey(), selectedRoomIds, dark, scale) {
        if (selectedRoomIds.isEmpty()) {
            null
        } else {
            renderSelection(map, selectedRoomIds.toSet(), if (dark) DarkPalette else LightPalette, scale)
        }
    }
}

private fun renderSelection(map: ParsedMap, selected: Set<Int>, palette: MapPalette, scale: Int): ImageBitmap {
    val bitmap = ImageBitmap(map.width * scale, map.height * scale)
    val canvas = Canvas(bitmap)
    canvas.scale(scale.toFloat(), scale.toFloat())
    val vivid = palette.rooms.map { vividOf(it) }
    val slots = roomColorSlots(map, palette.rooms.size)
    val paint = Paint()
    for (y in 0 until map.height) {
        val rowOff = y * map.width
        // Same Y flip as renderMap, so the tint lands on the floor it belongs to.
        val drawY = (map.height - 1 - y).toFloat()
        for (x in 0 until map.width) {
            val room = map.roomIdAtCell(rowOff + x) ?: continue
            if (room !in selected) continue
            // Indexed the way drawFloor tints a room, so a room lights up in its own hue.
            paint.color = vivid[slots[room] ?: 0]
            canvas.drawRect(Rect(x.toFloat(), drawY, x + 1f, drawY + 1f), paint)
        }
    }
    return bitmap
}

private fun renderWalls(map: ParsedMap, color: Color, scale: Int): ImageBitmap {
    val bitmap = ImageBitmap(map.width * scale, map.height * scale)
    val canvas = Canvas(bitmap)
    canvas.scale(scale.toFloat(), scale.toFloat())
    val paint = Paint().apply { this.color = color }
    for (y in 0 until map.height) {
        val rowOff = y * map.width
        // Same Y flip as renderMap, so a wall lands exactly on the wall cell in the floor bitmap.
        val drawY = (map.height - 1 - y).toFloat()
        for (x in 0 until map.width) {
            if ((map.grid[rowOff + x].toInt() and 0xff) != 127) continue
            canvas.drawRect(Rect(x.toFloat(), drawY, x + 1f, drawY + 1f), paint)
        }
    }
    return bitmap
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
    drawFloor(canvas, map, palette)
    drawOverlays(canvas, map, palette, drawRestrictions)
    return bitmap
}

/**
 * One of the map's two layers, drawn [scale] times larger than the grid. The detail screen draws
 * the floor, then a selected room's tint, then this overlay (path, zones, markers), so a lit-up
 * room never hides the path across it. Drawn larger, a thin path or a marker's edge stays crisp
 * when zoomed in instead of being one blurred grid cell.
 */
private fun renderLayer(map: ParsedMap, palette: MapPalette, scale: Int, overlay: Boolean): ImageBitmap {
    val bitmap = ImageBitmap(map.width * scale, map.height * scale)
    val canvas = Canvas(bitmap)
    canvas.scale(scale.toFloat(), scale.toFloat())
    if (overlay) drawOverlays(canvas, map, palette, drawRestrictions = true, includeRoute = false) else drawFloor(canvas, map, palette)
    return bitmap
}

private fun drawFloor(canvas: Canvas, map: ParsedMap, palette: MapPalette) {
    // The cells are masked by a rounded outline of the plan, so its edges are smooth rather than
    // stair steps. The mask is a path drawn over them, which anti-aliases where a clip would not.
    canvas.saveLayer(Rect(0f, 0f, map.width.toFloat(), map.height.toFloat()), Paint())
    val wallPaint = Paint().apply { color = palette.wall }
    val floorPaint = Paint().apply { color = palette.floor }
    val roomPaint = Paint()
    val patterns = HashMap<Int, FloorPattern>()
    val slots = roomColorSlots(map, palette.rooms.size)
    val w = map.width
    val h = map.height
    val grid = map.grid
    val raw = map.originalGrid
    val carpet = map.carpetMask
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
                        val base = palette.rooms[slots[roomId] ?: 0]
                        val pattern = patterns.getOrPut(roomId) {
                            floorPattern(map.segmentMaterials[roomId], map.segmentMaterialDirections[roomId])
                        }
                        val floor = if (pattern == FloorPattern.NONE) base else shaded(base, textureShade(pattern, x, y))
                        roomPaint.color = if (carpet != null && carpet[rowOff + x].toInt() == 1) shaded(floor, carpetShade(x, y)) else floor
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
    canvas.drawPath(mapOutlinePath(map), Paint().apply { blendMode = BlendMode.DstIn })
    canvas.restore()
}

private fun drawOverlays(canvas: Canvas, map: ParsedMap, palette: MapPalette, drawRestrictions: Boolean, includeRoute: Boolean = true) {
    if (includeRoute && map.pathMm.size >= 2) drawPath(canvas, map, palette)
    if (drawRestrictions) {
        map.carpetAreas.forEach { drawZone(canvas, map, it, CARPET_FILL, CARPET_STROKE) }
        map.thresholds.forEach { drawZone(canvas, map, it, THRESHOLD_FILL, THRESHOLD_STROKE) }
        map.noMopZones.forEach { drawZone(canvas, map, it, NO_MOP_FILL, NO_MOP_STROKE) }
        map.noGoZones.forEach { drawZone(canvas, map, it, NO_GO_FILL, NO_GO_STROKE) }
        map.virtualWalls.forEach { drawWall(canvas, map, it) }
    }
    if (includeRoute) drawMarkers(canvas, map, palette)
}

// Square dock, round robot: the two markers stay distinguishable without relying on colour.
private fun drawMarkers(canvas: Canvas, map: ParsedMap, palette: MapPalette) {
    map.chargerMm?.let { drawMarker(canvas, map, it, palette.charger, palette.markerRing, square = true) }
    map.robotMm?.let { drawMarker(canvas, map, it, palette.robot, palette.markerRing, square = false) }
}

/**
 * The route and the two markers, drawn straight to the screen through the map's transform instead of
 * into a bitmap, so they stay sharp at any zoom. [scale] is how many layer pixels make one cell.
 */
internal fun DrawScope.drawRouteAndMarkers(map: ParsedMap, view: MapView, scale: Int, dark: Boolean) {
    val palette = if (dark) DarkPalette else LightPalette
    withTransform({
        translate(left = view.canvasW / 2f + view.pan.x, top = view.canvasH / 2f + view.pan.y)
        scale(view.k, view.k, Offset.Zero)
        rotate(view.yawDegrees, Offset.Zero)
        scale(1f, view.squash, Offset.Zero)
        translate(left = -view.centerX, top = -view.centerY)
        scale(scale.toFloat(), scale.toFloat(), Offset.Zero)
    }) {
        if (map.pathMm.size >= 2) drawPath(drawContext.canvas, map, palette)
        drawMarkers(drawContext.canvas, map, palette)
    }
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
    val main = Path()
    val pure = Path()
    val wash = Path()
    var kind = -1
    var started = false
    for ((i, point) in map.pathMm.withIndex()) {
        val flag = map.pathFlags.getOrElse(i) { 0 }
        val now = when {
            flag and 0b1010 != 0 -> 2
            flag and 0b100 != 0 -> 1
            else -> 0
        }
        val cellX = (point.x / 50f) - map.pixelOffsetLeft
        val cellY = (map.height - 1) - ((point.y / 50f) - map.pixelOffsetTop)
        if (cellX !in 0f..map.width.toFloat() || cellY !in 0f..map.height.toFloat()) {
            started = false
            continue
        }
        val target = when (now) { 2 -> wash; 1 -> pure; else -> main }
        if (!started || now != kind) target.moveTo(cellX, cellY) else target.lineTo(cellX, cellY)
        started = true
        kind = now
    }
    // A wide faint band for the ground the robot has covered, under the thin line of its route.
    canvas.drawPath(main, Paint().apply {
        color = Color(0x4CE5E5E5) // the app's mopPathColor, #e5e5e54c
        style = PaintingStyle.Stroke
        strokeWidth = 6.5f
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    })
    for (line in listOf(main, pure)) {
        canvas.drawPath(line, Paint().apply {
            color = palette.path
            style = PaintingStyle.Stroke
            strokeWidth = 0.9f
            strokeCap = StrokeCap.Round
        })
    }
    // The trips to the dock to wash the mop are dashed.
    canvas.drawPath(wash, Paint().apply {
        color = Color(0x33FFFFFF) // the app's backWashPathColor, #FFFFFF33
        style = PaintingStyle.Stroke
        strokeWidth = 0.9f
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 3f))
    })
}

private fun drawMarker(
    canvas: Canvas,
    map: ParsedMap,
    point: ParsedMapPoint,
    color: Color,
    ring: Color,
    square: Boolean,
    radius: Float = 5.5f,
) {

    val cellX = (point.x / 50f) - map.pixelOffsetLeft
    val cellYBottom = (point.y / 50f) - map.pixelOffsetTop
    // Flip Y to match the rendered orientation above.
    val cellY = (map.height - 1) - cellYBottom
    if (cellX !in 0f..map.width.toFloat() || cellY !in 0f..map.height.toFloat()) return
    val ringPaint = Paint().apply { this.color = ring }
    val fillPaint = Paint().apply { this.color = color }
    if (square) {
        val r = radius * 0.8f
        canvas.drawRoundRect(cellX - r - 1f, cellY - r - 1f, cellX + r + 1f, cellY + r + 1f, 2.5f, 2.5f, ringPaint)
        canvas.drawRoundRect(cellX - r, cellY - r, cellX + r, cellY + r, 2f, 2f, fillPaint)
        // A small house for the dock.
        val house = Path().apply {
            moveTo(cellX, cellY - r * 0.6f)
            lineTo(cellX + r * 0.6f, cellY - r * 0.05f)
            lineTo(cellX + r * 0.4f, cellY - r * 0.05f)
            lineTo(cellX + r * 0.4f, cellY + r * 0.55f)
            lineTo(cellX - r * 0.4f, cellY + r * 0.55f)
            lineTo(cellX - r * 0.4f, cellY - r * 0.05f)
            lineTo(cellX - r * 0.6f, cellY - r * 0.05f)
            close()
        }
        canvas.drawPath(house, Paint().apply { this.color = Color.White })
    } else {
        canvas.drawCircle(Offset(cellX, cellY), radius + 1.5f, ringPaint)
        canvas.drawCircle(Offset(cellX, cellY), radius, fillPaint)
        // The robot seen from above: a white ring for its body and a dot for the lidar turret.
        val white = Paint().apply { this.color = Color.White }
        val hole = Paint().apply { this.color = color }
        canvas.drawCircle(Offset(cellX, cellY), radius * 0.62f, white)
        canvas.drawCircle(Offset(cellX, cellY), radius * 0.46f, hole)
        canvas.drawCircle(Offset(cellX, cellY - radius * 0.12f), radius * 0.2f, white)
    }
}
