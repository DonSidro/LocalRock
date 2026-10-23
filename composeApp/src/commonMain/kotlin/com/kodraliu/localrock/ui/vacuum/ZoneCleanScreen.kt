package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
    var drawMode by remember { mutableStateOf(true) } // true = draw zones, false = pan/zoom
    var mapScale by remember { mutableStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone Clean") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    // Draw / Pan toggle
                    FilledIconToggleButton(
                        checked = drawMode,
                        onCheckedChange = { drawMode = it },
                    ) {
                        Icon(
                            if (drawMode) Icons.Default.Edit else Icons.Default.OpenWith,
                            contentDescription = if (drawMode) "Draw mode" else "Pan mode",
                        )
                    }
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

                // Whole map, centred; again whenever the view or the map changes size (e.g. on rotation).
                LaunchedEffect(viewport, pm.width, pm.height) {
                    mapScale = 1f
                    mapOffset = clampMapOffset(Offset.Zero, 1f, pm.fittedSize(viewport), viewport)
                }

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged { viewport = it.toSize() }
                            .pointerInput(drawMode, pm) {
                                // Screen position -> map canvas position, kept on the map.
                                fun mapPoint(p: Offset): Offset {
                                    val content = pm.fittedSize(size.toSize())
                                    val c = (p - mapOffset) / mapScale
                                    return Offset(c.x.coerceIn(0f, content.width), c.y.coerceIn(0f, content.height))
                                }
                                if (drawMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            dragStart = mapPoint(offset)
                                            dragCurrent = dragStart
                                            sent = false
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            dragCurrent = mapPoint(change.position)
                                        },
                                        onDragEnd = {
                                            val start = dragStart ?: return@detectDragGestures
                                            val end = dragCurrent ?: return@detectDragGestures
                                            val content = pm.fittedSize(size.toSize())
                                            val a = pm.canvasToMm(start, content.width, content.height)
                                            val b = pm.canvasToMm(end, content.width, content.height)
                                            val mx1 = minOf(a.first, b.first)
                                            val my1 = minOf(a.second, b.second)
                                            val mx2 = maxOf(a.first, b.first)
                                            val my2 = maxOf(a.second, b.second)
                                            if (mx2 - mx1 > 500 && my2 - my1 > 500) {
                                                zones = zones + ZoneRect(mx1, my1, mx2, my2)
                                            }
                                            dragStart = null
                                            dragCurrent = null
                                        },
                                        onDragCancel = { dragStart = null; dragCurrent = null },
                                    )
                                } else {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val view = size.toSize()
                                        val (newScale, newOffset) =
                                            zoomMap(mapScale, mapOffset, centroid, pan, zoom, pm.fittedSize(view), view)
                                        mapScale = newScale
                                        mapOffset = newOffset
                                    }
                                }
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
                            if (drawMode && ds != null && dc != null) {
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
                        !drawMode -> "Pinch to zoom · Double-tap to reset · Switch to draw mode to add zones"
                        zones.isNotEmpty() -> "${zones.size} zone(s) — tap Start to begin"
                        else -> "Drag on the map to draw a zone · Switch to pan mode to navigate"
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
