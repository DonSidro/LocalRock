package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.ZoneRect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneCleanScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val scope = rememberCoroutineScope()
    var zones by remember { mutableStateOf<List<ZoneRect>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var sent by remember { mutableStateOf(false) }
    var drawMode by remember { mutableStateOf(true) } // true = draw zones, false = pan/zoom
    var mapScale by remember { mutableStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zone Clean") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
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
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { viewModel.repository.cleanZones(zones) }
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
                val mapAspect = pm.width.toFloat() / pm.height.toFloat()
                val zoneColor = MaterialTheme.colorScheme.primary

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(mapAspect)
                            .clipToBounds()
                            .pointerInput(drawMode, pm) {
                                if (drawMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            // Convert screen offset to canvas coordinates
                                            dragStart = Offset(
                                                (offset.x - mapOffset.x) / mapScale,
                                                (offset.y - mapOffset.y) / mapScale,
                                            )
                                            dragCurrent = dragStart
                                            sent = false
                                        },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            dragCurrent = dragCurrent?.plus(delta / mapScale)
                                        },
                                        onDragEnd = {
                                            val start = dragStart ?: return@detectDragGestures
                                            val end = dragCurrent ?: return@detectDragGestures
                                            val mapScaleX = pm.width.toFloat() / size.width
                                            val mapScaleY = pm.height.toFloat() / size.height
                                            val mx1 = (minOf(start.x, end.x) * mapScaleX).toInt()
                                            val my1 = (minOf(start.y, end.y) * mapScaleY).toInt()
                                            val mx2 = (maxOf(start.x, end.x) * mapScaleX).toInt()
                                            val my2 = (maxOf(start.y, end.y) * mapScaleY).toInt()
                                            if (mx2 - mx1 > 10 && my2 - my1 > 10) {
                                                zones = zones + ZoneRect(mx1, my1, mx2, my2)
                                            }
                                            dragStart = null
                                            dragCurrent = null
                                        },
                                        onDragCancel = { dragStart = null; dragCurrent = null },
                                    )
                                } else {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val newScale = (mapScale * zoom).coerceIn(1f, 8f)
                                        mapOffset = centroid - (centroid - mapOffset) * (newScale / mapScale) + pan
                                        val maxX = size.width * (newScale - 1f)
                                        val maxY = size.height * (newScale - 1f)
                                        mapOffset = Offset(
                                            mapOffset.x.coerceIn(-maxX, 0f),
                                            mapOffset.y.coerceIn(-maxY, 0f),
                                        )
                                        mapScale = newScale
                                    }
                                }
                            },
                    ) {
                        withTransform({
                            translate(left = mapOffset.x, top = mapOffset.y)
                            scale(mapScale, mapScale, pivot = Offset.Zero)
                        }) {
                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(bitmap.width, bitmap.height),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                                filterQuality = FilterQuality.None,
                            )

                            val canvasScaleX = size.width / pm.width
                            val canvasScaleY = size.height / pm.height
                            val strokePx = (2.dp.toPx() / mapScale).coerceAtLeast(1f)

                            for (zone in zones) {
                                val left = zone.x1 * canvasScaleX
                                val top = zone.y1 * canvasScaleY
                                val w = (zone.x2 - zone.x1) * canvasScaleX
                                val h = (zone.y2 - zone.y1) * canvasScaleY
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
