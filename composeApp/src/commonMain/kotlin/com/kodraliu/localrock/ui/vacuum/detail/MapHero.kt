package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import com.kodraliu.localrock.ui.strings.DeviceStrings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.roomAtNorm

@Composable
internal fun MapHeroSection(
    deviceId: String,
    parsedMap: ParsedMap?,
    offlineLines: List<String>,
    busy: Boolean,
    selectedRoomIds: List<Int>,
    mapLoading: Boolean = false,
    sessionRoomNames: List<String>,
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    rotationDegrees: Int,
    yaw3D: Float,
    onYawBy: (Float) -> Unit,
    tilt3D: Float,
    onTiltBy: (Float) -> Unit,
    is3D: Boolean,
    onLoadMap: () -> Unit,
    onRecover: () -> Unit,
    onRoomTap: (Int) -> Unit,
    onObstacleTap: (MapObstacle) -> Unit,
    onFloorSwitch: (Int) -> Unit,
    onEditMap: () -> Unit,
    onRotate: () -> Unit,
    onToggle3D: () -> Unit,
    mapViewport: LiveMapViewport,
    faintRoute: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Black in both themes, as in the Roborock app: the bright room colours need it, and the gaps in
    // the plan then read as unscanned instead of grey floor.
    val heroBackground = MAP_HERO_BACKGROUND
    Box(
        modifier = modifier
            .clipToBounds()
            .background(heroBackground),
    ) {
        if (parsedMap != null) {
            VacuumMapImage(
                map = parsedMap,
                viewportScope = deviceId to currentFloorFlag,
                selectedRoomIds = selectedRoomIds,
                onRoomTap = onRoomTap,
                onObstacleTap = onObstacleTap,
                is3D = is3D,
                // The two views keep separate headings, so turning the 3D model never moves the 2D map.
                yawDegrees = if (is3D) yaw3D else rotationDegrees.toFloat(),
                onYawBy = onYawBy,
                tiltDegrees = tilt3D,
                onTiltBy = onTiltBy,
                providedMapViewport = mapViewport,
                faintRoute = faintRoute,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (mapLoading) MapLoadingState(Modifier.fillMaxSize())
            else MapEmptyState(busy = busy, onLoadMap = onLoadMap, onRecover = onRecover, modifier = Modifier.fillMaxSize())
        }

        // Refresh and Edit map are the only controls on top of the map; camera, remote and map
        // recovery live in the map picker so the hero reads as one surface. Edit sits in the
        // opposite corner so the two can't be confused, and only once there is a map to edit.
        if (parsedMap != null) {
            FilledTonalIconButton(
                onClick = onEditMap,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(48.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit map", modifier = Modifier.size(22.dp))
            }
        }

        FilledTonalIconButton(
            onClick = onLoadMap,
            enabled = !busy,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(48.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = "Refresh map", modifier = Modifier.size(22.dp))
        }

        if (parsedMap != null) {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = onToggle3D, modifier = Modifier.size(40.dp)) {
                    Text(if (is3D) "3D" else "2D", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                FilledTonalIconButton(onClick = onRotate, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate map 90°", modifier = Modifier.size(20.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (floorMaps.isNotEmpty()) {
                FloorPicker(
                    floorMaps = floorMaps,
                    currentFloorFlag = currentFloorFlag,
                    enabled = !busy,
                    onFloorSwitch = onFloorSwitch,
                    onManageMaps = onRecover,
                )
            }
            if (sessionRoomNames.isNotEmpty()) {
                SessionRoomsBadge(roomNames = sessionRoomNames)
            }
            OfflineSnapshotBadge(offlineLines)
        }
    }
}

/** Loaded map's name over the map; tapping it lists the other saved maps. */
@Composable
private fun FloorPicker(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    enabled: Boolean,
    onFloorSwitch: (Int) -> Unit,
    onManageMaps: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = floorMaps.find { it.mapFlag == currentFloorFlag }
    Box {
        Surface(
            onClick = { expanded = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    current?.name ?: "Choose map",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch map")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            floorMaps.forEach { floor ->
                val selected = floor.mapFlag == currentFloorFlag
                DropdownMenuItem(
                    text = { Text(floor.name) },
                    leadingIcon = {
                        if (selected) Icon(Icons.Default.Check, contentDescription = "Loaded")
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        expanded = false
                        if (!selected) onFloorSwitch(floor.mapFlag)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(DeviceStrings.MANAGE_SAVED_MAPS) }, onClick = { expanded = false; onManageMaps() })
        }
    }
}

@Composable
private fun SessionRoomsBadge(roomNames: List<String>, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 220.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulsingDot(MaterialTheme.colorScheme.onPrimary)
            Text(
                text = "Cleaning ${roomNames.joinToString(", ")}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Stylised, not to scale: tall enough that walls read as walls instead of thin borders.
private const val WALL_HEIGHT_CELLS = 7f

private const val MIN_ZOOM = 0.4f

private const val MAX_ZOOM = 8f

// Degrees of tilt per screen pixel two fingers are dragged vertically; a full-screen drag is about 70°.
private const val TILT_DEGREES_PER_PX = 0.05f

private fun DrawScope.drawMapLayer(image: ImageBitmap, view: MapView, height: Float, quality: FilterQuality) {
    withTransform({ transform(view.layerMatrix(height)) }) {
        val size = IntSize(image.width, image.height)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = size,
            dstOffset = IntOffset.Zero,
            dstSize = size,
            filterQuality = quality,
        )
    }
}

private val MIN_LABEL_WIDTH = 36.dp

/** The widest a name may be: the whole view when the room's width is unknown, else its room's, at least [minPx]. */
internal fun labelTextWidth(spanKnown: Boolean, roomPx: Float, minPx: Int, viewPx: Int): Int =
    if (!spanKnown) viewPx else roomPx.toInt().coerceAtLeast(minPx).coerceAtMost(viewPx)

private val LABEL_PILL_FLAT = Color.White.copy(alpha = 0.8f)
private val LABEL_PILL_3D = Color.Black.copy(alpha = 0.6f)

@Composable
internal fun VacuumMapImage(
    map: ParsedMap,
    selectedRoomIds: List<Int>,
    onRoomTap: (Int) -> Unit,
    onObstacleTap: (MapObstacle) -> Unit,
    is3D: Boolean,
    yawDegrees: Float,
    onYawBy: (Float) -> Unit,
    tiltDegrees: Float,
    onTiltBy: (Float) -> Unit,
    modifier: Modifier = Modifier,
    viewportScope: Any? = null,
    providedMapViewport: LiveMapViewport? = null,
    faintRoute: Boolean = false,
) {
    // Drawn larger than the grid so a zoomed-in view stays crisp; every length below that is measured
    // in the layers' own pixels (wall height, label lift) is in cells times this.
    val scale = remember(map) { supersampleFor(map) }
    val (bitmap, overlayBitmap) = rememberMapLayers(map, scale)
    val darkMap = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val walls = rememberWallBitmaps(map, scale, enabled = is3D)
    val selectionBitmap = rememberSelectionBitmap(map, selectedRoomIds, scale)
    val floorShapes = remember(map.floorKey()) { map.floorPlanShapes() }
    val wallHeight = WALL_HEIGHT_CELLS * scale
    val labelRooms = remember(map.rooms, map.eraserZones) { map.rooms.filter { it.labelNormX != null && it.labelNormY != null && it.id !in map.erasedRoomIds } }
    val textMeasurer = rememberTextMeasurer()
    // Labels follow the scheme rather than being a fixed black scrim: over a light map a dark
    // scrim reads as a scattering of heavy blobs and dominates the surface it sits on.
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF0E1A26),
    )
    val selectedBgColor = MaterialTheme.colorScheme.primary
    // In dark mode `primary` is a bright cyan, so white-on-primary would be unreadable.
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onPrimary)
    val roomIcons = rememberRoomIconPainters()
    val currentOnRoomTap by rememberUpdatedState(onRoomTap)
    val currentOnObstacleTap by rememberUpdatedState(onObstacleTap)
    // Where the names were last drawn; filled while drawing, read by the tap handler.
    val labelHits = remember { mutableListOf<RoomLabelHit>() }
    val currentYaw by rememberUpdatedState(yawDegrees)
    val currentOnYawBy by rememberUpdatedState(onYawBy)
    val currentTilt by rememberUpdatedState(tiltDegrees)
    val currentOnTiltBy by rememberUpdatedState(onTiltBy)

    // The view is framed on the floor plan, once per map identity: a live update replaces the map object every
    // few seconds, and a plan that is still growing must not re-centre and rescale under the user's fingers.
    val identity = map.viewportIdentity(viewportScope)
    val bounds = remember(identity) { map.contentBoundsNorm() }
    val mapViewport = providedMapViewport ?: remember { LiveMapViewport() }
    LaunchedEffect(identity, is3D) { mapViewport.frameIfNeeded(identity to is3D) }
    // The gesture handlers outlive a live update, so they read the current map and view, not the ones they started with.
    val currentMap by rememberUpdatedState(map)
    val currentBitmap by rememberUpdatedState(bitmap)

    fun viewFor(width: Float, height: Float): MapView {
        // Fitted as if looking straight down, so the plan keeps its size as the camera is tilted
        // instead of shrinking and growing under the fingers.
        val fit = fitScale(
            width, height,
            bounds.width * bitmap.width, bounds.height * bitmap.height,
            currentYaw, squash = 1f, freeTurning = is3D,
        ) * (if (is3D) 1.1f else 1f)
        return framedView(
            canvasW = width,
            canvasH = height,
            centerX = (bounds.left + bounds.right) / 2f * bitmap.width,
            centerY = (bounds.top + bounds.bottom) / 2f * bitmap.height,
            fit = fit,
            zoom = mapViewport.zoom,
            pan = mapViewport.pan,
            yawDegrees = currentYaw,
            squash = if (is3D) squashForTilt(currentTilt) else 1f,
        )
    }
    val currentViewFor by rememberUpdatedState(::viewFor)
    val drawn = remember { DrawnView() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val obstacleIcons = rememberObstacleIcons()
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(identity, is3D) {
                    // Pinch zooms about the fingers. In 3D the model behaves like a maps app's 3D
                    // view: one finger moves it, a two-finger twist turns it to any heading, and two
                    // fingers dragged up or down together tip the camera. The flat map keeps the
                    // heading chosen with the rotate button, and two fingers pan it.
                    detectMapGestures { centroid, panChange, zoomChange, rotation, fingers ->
                        val newZoom = (mapViewport.zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val tilting = is3D && isTiltDrag(fingers, panChange, zoomChange, rotation)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        // The pan is kept in units of the framed view; this works in pixels and converts back.
                        val fit = drawn.forHitTest { currentViewFor(size.width.toFloat(), size.height.toFloat()) }.k / mapViewport.zoom
                        val panPx = mapViewport.pan * fit
                        val moved = (centroid - center) - ((centroid - center) - panPx) * (newZoom / mapViewport.zoom) +
                            (if (tilting) Offset.Zero else panChange)
                        // Loose clamp so the plan can't be flung entirely out of reach.
                        mapViewport.pan = Offset(
                            moved.x.coerceIn(-size.width.toFloat(), size.width.toFloat()),
                            moved.y.coerceIn(-size.height.toFloat(), size.height.toFloat()),
                        ) / fit
                        mapViewport.zoom = newZoom
                        if (is3D && rotation != 0f) currentOnYawBy(rotation)
                        // Dragging up tips the camera toward the horizon, as in a maps app.
                        if (tilting && panChange.y != 0f) currentOnTiltBy(-panChange.y * TILT_DEGREES_PER_PX)
                    }
                }
                .pointerInput(identity, is3D) {
                    detectTapGestures(
                        // Double tap returns to the framed view; the heading is left as it is.
                        onDoubleTap = { mapViewport.reset() },
                        onTap = { tap ->
                            // An obstacle dot is small, so a tap near one opens it before any room.
                            val tapView = drawn.forHitTest { currentViewFor(size.width.toFloat(), size.height.toFloat()) }
                            currentMap.obstacleAt(tap, tapView, currentBitmap.width / currentMap.width.toFloat(), 20.dp.toPx())
                                ?.let { currentOnObstacleTap(it); return@detectTapGestures }
                            // The same transform that drew the map, run backwards, so a tap lands on
                            // the room under the finger at any heading, zoom or tilt.
                            roomAtLabel(tap, labelHits)?.let { currentOnRoomTap(it); return@detectTapGestures }
                            currentMap.roomIdAtTap(tapView, tap, currentBitmap.width, currentBitmap.height)
                                ?.let { currentOnRoomTap(it) }
                        },
                    )
                },
        ) {
            val view = viewFor(size.width, size.height)
            drawn.view = view
            // Magnified past 2 screen pixels per layer pixel, nearest-neighbour keeps edges crisp
            // where smoothing would blur them; so does a flat quarter-turn, which maps pixels 1:1.
            // Zoomed out, where many pixels share one on screen, smoothing avoids shimmer.
            val flatQuarterTurn = !is3D && yawDegrees % 90f == 0f
            val quality = if ((flatQuarterTurn && view.k >= 0.95f) || view.k >= 2f) FilterQuality.None else FilterQuality.Low
            drawMapLayer(bitmap, view, 0f, quality)
            // A selected room is tinted over the floor, and the path goes over the tint, so the
            // choice reads at a glance without hiding where the robot has been.
            selectionBitmap?.let { drawMapLayer(it, view, 0f, quality) }
            drawFloorPlanStrokes(floorShapes, view, scale)
            drawMapLayer(overlayBitmap, view, 0f, quality)
            drawRouteAndMarkers(map, view, scale, darkMap, faintRoute)
            if (is3D && walls != null) {
                val (wallSide, wallTop) = walls
                // Walls rise by stacking the wall layer at climbing heights, then capping it with the
                // lighter top. Each layer goes through the same transform as the floor, so the
                // extrusion lines up at every heading.
                val steps = (wallHeight * view.k * view.lift / 1.5f).toInt().coerceIn(2, 30)
                for (i in 1..steps) drawMapLayer(wallSide, view, wallHeight * i / steps, quality)
                drawMapLayer(wallTop, view, wallHeight, quality)
            }

            // Under the labels, so a name is never hidden by a badge. A tap still opens the obstacle first.
            drawObstacles(map, view, bitmap.width / map.width.toFloat(), obstacleIcons)

            // Labels are drawn outside the zoom transform so they stay a constant, legible size
            // instead of growing to 8x with the map. Selected rooms carry their cleaning order,
            // so selection reads without relying on the highlight colour alone.
            labelHits.clear()
            for (room in labelRooms) {
                val normX = room.labelNormX ?: continue
                val normY = room.labelNormY ?: continue
                val at = view.toScreen(
                    normX * bitmap.width,
                    normY * bitmap.height,
                    height = if (is3D) wallHeight * 0.5f else 0f,
                )
                val cx = at.x
                val cy = at.y
                if (cx < 0f || cx > size.width || cy < 0f || cy > size.height) continue
                val order = selectedRoomIds.indexOf(room.id)
                val selected = order >= 0
                val text = if (selected) "${order + 1} · ${room.name}" else room.name
                // Dark names on a light pill over the flat map; white names on a dark pill over the black 3D walls.
                val style = when {
                    selected -> selectedLabelStyle
                    is3D -> labelStyle.copy(color = Color.White)
                    else -> labelStyle
                }
                // Each name has a round badge with its room type in front, as in the Roborock app. A room the
                // robot's room table has no entry for shows the badge alone. A selected room keeps its numbered pill.
                val badge = if (selected) 0f else 16.dp.toPx()
                val badgeGap = if (selected) 0f else 3.dp.toPx()
                val unnamed = !(room.inRoomTable || room.name != "Room ${room.id}") && !selected
                val padX = 6.dp.toPx()
                val padY = 3.dp.toPx()
                val edge = 4.dp.toPx()
                // A name is shortened to fit inside its room, so it never runs out over the black between rooms,
                // and never wider than the view so it is not cut off at the screen edge.
                val yawRad = view.yawDegrees * (PI / 180.0)
                val spanCells = abs(cos(yawRad)) * (room.labelSpanCells ?: 0) + abs(sin(yawRad)) * (room.labelSpanCellsY ?: room.labelSpanCells ?: 0)
                val roomWidthPx = spanCells.toFloat() * view.k * scale
                val viewRoom = (size.width - 2 * (padX + edge) - badge - badgeGap).toInt().coerceAtLeast(1)
                // A room with no known width (a B01 map) gets the whole view; the others are fitted to their room.
                val textRoom = labelTextWidth(room.labelSpanCells != null, roomWidthPx - badge - badgeGap, MIN_LABEL_WIDTH.roundToPx(), viewRoom)
                val measured = textMeasurer.measure(
                    text, style,
                    overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                    constraints = Constraints(maxWidth = textRoom),
                )
                val tw = if (unnamed) 0f else measured.size.width.toFloat()
                val th = measured.size.height.toFloat()
                val total = if (selected) tw else badge + (if (unnamed) 0f else badgeGap + tw)
                val rowH = maxOf(th, badge)
                val pillW = total + padX * 2
                val pillH = rowH + padY * 2
                // The label stays inside the view: a name near the edge slides in instead of being cut off.
                val lx = if (size.width > pillW + 2 * edge) cx.coerceIn(pillW / 2 + edge, size.width - pillW / 2 - edge) else cx
                val ly = if (size.height > pillH + 2 * edge) cy.coerceIn(pillH / 2 + edge, size.height - pillH / 2 - edge) else cy
                val pillTopLeft = Offset(lx - pillW / 2, ly - pillH / 2)
                // A backing behind every name keeps it readable where it reaches past its room onto the black.
                if (!unnamed) {
                    drawRoundRect(
                        color = when {
                            selected -> selectedBgColor
                            is3D -> LABEL_PILL_3D
                            else -> LABEL_PILL_FLAT
                        },
                        topLeft = pillTopLeft,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(pillH / 2),
                    )
                }
                labelHits += RoomLabelHit(room.id, Rect(pillTopLeft, Size(pillW, pillH)))
                val left = lx - total / 2
                if (!selected) {
                    val centre = Offset(left + badge / 2, ly)
                    drawCircle(color = Color(0xFF14202B), radius = badge / 2, center = centre)
                    val glyph = badge * 0.6f
                    withTransform({ translate(left = centre.x - glyph / 2, top = centre.y - glyph / 2) }) {
                        with(roomIcons.forTag(room.tag)) {
                            draw(size = Size(glyph, glyph), colorFilter = ColorFilter.tint(Color.White))
                        }
                    }
                }
                // Drawn from the measured layout, so a name near the screen edge is not re-wrapped to fit.
                if (!unnamed) drawText(measured, topLeft = Offset(left + badge + badgeGap, ly - th / 2))
            }
        }

        // Zoom is shown relative to the framed view, which is the resting state. It sits at the bottom
        // centre because the floor switcher and the session rooms badge use the top left.
        if (mapViewport.zoom > 1.05f) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "${(mapViewport.zoom * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

    }
}

@Composable
private fun MapLoadingState(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(DeviceStrings.LOADING_MAP, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun MapEmptyState(busy: Boolean, onLoadMap: () -> Unit, onRecover: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The map area is black in both themes, so this text is light rather than the theme colour.
        Icon(Icons.Default.Map, null, Modifier.size(48.dp), Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text("No map loaded", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text("Load the latest map from your robot.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLoadMap, enabled = !busy) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "Loading…" else "Load map")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRecover, enabled = !busy) {
            Text("Saved maps")
        }
    }
}
