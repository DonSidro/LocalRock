package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kodraliu.localrock.shared.vacuum.ZoneRect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneCleanScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var zones by remember { mutableStateOf<List<ZoneRect>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var sent by remember { mutableStateOf(false) }
    var mapScale by remember { mutableStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    // The map size the view was last framed for: a new map size frames it again, a resized view keeps the zoom.
    var framedFor by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone Clean") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (zones.isNotEmpty()) {
                        IconButton(onClick = { zones = emptyList(); sent = false }) {
                            Icon(Icons.Default.Delete, "Clear zones")
                        }
                    }
                    if (zones.isNotEmpty() && !sent) {
                        TextButton(enabled = !busy, onClick = {
                            val selectedZones = zones
                            viewModel.run {
                                it.cleanZones(selectedZones)
                                sent = true
                            }
                        }) { Text("Start") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (parsedMap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Map, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Map not loaded", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Go back and wait for the map to load first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                val pm = parsedMap!!
                val bitmap = rememberMapBitmap(pm)
                val zoneColor = MaterialTheme.colorScheme.primary

                // Whole map, centred, when the map first shows or changes size. A view that only changes
                // size (rotation, or the hint below changing length) keeps the user's zoom (#101).
                LaunchedEffect(viewport, pm.width, pm.height) {
                    if (viewport.width <= 0f || viewport.height <= 0f) return@LaunchedEffect
                    val mapSize = pm.width to pm.height
                    if (framedFor != mapSize) {
                        framedFor = mapSize
                        // Framed on the floor plan, as on the robot screen: the grid is mostly empty around it.
                        val (scale, offset) = frameMap(pm.contentBoundsNorm(), pm.fittedSize(viewport), viewport)
                        mapScale = scale
                        mapOffset = offset
                    } else {
                        mapOffset = clampMapOffset(mapOffset, mapScale, pm.fittedSize(viewport), viewport)
                    }
                }
                // The gesture handlers outlive a map refresh, so they read the current map instead of restarting.
                val currentMap by rememberUpdatedState(pm)

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged { viewport = it.toSize() }
                            .pointerInput(Unit) {
                                // Double tap returns to the framed floor plan.
                                detectTapGestures(onDoubleTap = {
                                    val view = size.toSize()
                                    val (scale, offset) = frameMap(currentMap.contentBoundsNorm(), currentMap.fittedSize(view), view)
                                    mapScale = scale
                                    mapOffset = offset
                                })
                            }
                            .pointerInput(Unit) {
                                // Screen position -> map canvas position, kept on the map.
                                fun mapPoint(p: Offset): Offset {
                                    val content = currentMap.fittedSize(size.toSize())
                                    val c = (p - mapOffset) / mapScale
                                    return Offset(c.x.coerceIn(0f, content.width), c.y.coerceIn(0f, content.height))
                                }
                                // One finger draws a zone; two fingers pinch and move the map (#101).
                                detectZoneGestures(
                                    onDrawStart = { offset ->
                                        dragStart = mapPoint(offset)
                                        dragCurrent = dragStart
                                        sent = false
                                    },
                                    onDraw = { position -> dragCurrent = mapPoint(position) },
                                    onDrawEnd = {
                                        val start = dragStart
                                        val end = dragCurrent
                                        dragStart = null
                                        dragCurrent = null
                                        if (start != null && end != null) {
                                            val map = currentMap
                                            val content = map.fittedSize(size.toSize())
                                            val a = map.canvasToMm(start, content.width, content.height)
                                            val b = map.canvasToMm(end, content.width, content.height)
                                            val mx1 = minOf(a.first, b.first)
                                            val my1 = minOf(a.second, b.second)
                                            val mx2 = maxOf(a.first, b.first)
                                            val my2 = maxOf(a.second, b.second)
                                            if (mx2 - mx1 > 500 && my2 - my1 > 500) {
                                                zones = zones + ZoneRect(mx1, my1, mx2, my2)
                                            }
                                        }
                                    },
                                    onDrawCancel = { dragStart = null; dragCurrent = null },
                                    onTransform = { centroid, pan, zoom ->
                                        val view = size.toSize()
                                        val (newScale, newOffset) =
                                            zoomMap(mapScale, mapOffset, centroid, pan, zoom, currentMap.fittedSize(view), view)
                                        mapScale = newScale
                                        mapOffset = newOffset
                                    },
                                )
                            },
                    ) {
                        val content = pm.fittedSize(size)
                        withTransform({
                            translate(left = mapOffset.x, top = mapOffset.y)
                            scale(mapScale, mapScale, pivot = Offset.Zero)
                        }) {
                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bitmap.width, bitmap.height),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(content.width.toInt(), content.height.toInt()),
                                filterQuality = FilterQuality.None,
                            )

                            val strokePx = (2.dp.toPx() / mapScale).coerceAtLeast(1f)

                            for (zone in zones) {
                                val a = pm.mmToCanvas(zone.x1, zone.y1, content.width, content.height)
                                val b = pm.mmToCanvas(zone.x2, zone.y2, content.width, content.height)
                                val left = minOf(a.x, b.x)
                                val top = minOf(a.y, b.y)
                                val w = maxOf(a.x, b.x) - left
                                val h = maxOf(a.y, b.y) - top
                                drawRect(color = zoneColor.copy(alpha = 0.3f), topLeft = Offset(left, top), size = Size(w, h))
                                drawRect(color = zoneColor, topLeft = Offset(left, top), size = Size(w, h), style = Stroke(strokePx))
                            }

                            val ds = dragStart
                            val dc = dragCurrent
                            if (ds != null && dc != null) {
                                val left = minOf(ds.x, dc.x)
                                val top = minOf(ds.y, dc.y)
                                val w = maxOf(ds.x, dc.x) - left
                                val h = maxOf(ds.y, dc.y) - top
                                drawRect(color = zoneColor.copy(alpha = 0.15f), topLeft = Offset(left, top), size = Size(w, h))
                                drawRect(
                                    color = zoneColor,
                                    topLeft = Offset(left, top),
                                    size = Size(w, h),
                                    style = Stroke(
                                        width = strokePx,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / mapScale, 6f / mapScale)),
                                    ),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = when {
                        sent -> "Zone cleaning started!"
                        zones.isNotEmpty() -> "${zones.size} zone(s) — tap Start to begin"
                        else -> "Drag to draw a zone · Two fingers to zoom and move · Double-tap to reset the view"
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * One finger drags out a zone. As soon as a second finger lands the touch becomes a pinch and pan of
 * the map until every finger is lifted, and a zone being drawn is dropped. Nothing is drawn before the
 * finger has moved past the touch slop, so a tap still reaches the double-tap detector.
 */
private suspend fun PointerInputScope.detectZoneGestures(
    onDrawStart: (Offset) -> Unit,
    onDraw: (Offset) -> Unit,
    onDrawEnd: () -> Unit,
    onDrawCancel: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var drawing = false
        var transforming = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2 || transforming) {
                if (!transforming) {
                    transforming = true
                    if (drawing) {
                        drawing = false
                        onDrawCancel()
                    }
                }
                if (pressed >= 2) {
                    onTransform(event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom())
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } else {
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                    if (!drawing && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        drawing = true
                        onDrawStart(down.position)
                    }
                    if (drawing) {
                        onDraw(change.position)
                        change.consume()
                    }
                }
            }
        } while (event.changes.any { it.pressed })
        if (drawing) onDrawEnd()
    }
}
