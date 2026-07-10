package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlinx.coroutines.launch

private val NO_GO_COLOR = Color(0xfff44336)
private val NO_MOP_COLOR = Color(0xff2196f3)

private fun colorFor(kind: ZoneKind) = if (kind == ZoneKind.NO_GO) NO_GO_COLOR else NO_MOP_COLOR

/**
 * Editor for persistent no-go / no-mop zones. Draw rectangles on the map, tap to select+delete,
 * then Save persists the full set to the robot via [com.kodraliu.localrock.shared.vacuum.VacuumRepository.saveZones].
 * All zones are held in robot millimetre coordinates so they round-trip through the parser/renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapEditScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val scope = rememberCoroutineScope()

    var zones by remember { mutableStateOf<List<MapZone>>(emptyList()) }
    var initialized by remember { mutableStateOf(false) }
    var activeKind by remember { mutableStateOf(ZoneKind.NO_GO) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var drawMode by remember { mutableStateOf(true) } // true = draw, false = pan/zoom
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var mapScale by remember { mutableStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }

    // Seed the editor once from the first loaded map; local edits own the state after that so the
    // 5s map poll doesn't wipe in-progress work.
    LaunchedEffect(parsedMap) {
        if (!initialized && parsedMap != null) {
            zones = parsedMap!!.noGoZones + parsedMap!!.noMopZones
            initialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("No-go zones") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    FilledIconToggleButton(checked = drawMode, onCheckedChange = { drawMode = it }) {
                        Icon(
                            if (drawMode) Icons.Default.Edit else Icons.Default.OpenWith,
                            contentDescription = if (drawMode) "Draw mode" else "Pan mode",
                        )
                    }
                    if (dirty && !saving) {
                        TextButton(onClick = {
                            scope.launch {
                                saving = true
                                val noGo = zones.filter { it.kind == ZoneKind.NO_GO }
                                val noMop = zones.filter { it.kind == ZoneKind.NO_MOP }
                                runCatching { viewModel.repository.saveZones(noGo, noMop) }
                                saving = false
                                dirty = false
                                saved = true
                            }
                        }) { Text("Save") }
                    }
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val pm = parsedMap
            if (pm == null) {
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
                return@Column
            }

            val bitmap = rememberMapBitmap(pm)
            val mapAspect = pm.width.toFloat() / pm.height.toFloat()

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(mapAspect)
                        .clipToBounds()
                        .pointerInput(pm) {
                            // Tap to select/deselect a zone for deletion (works in either mode).
                            detectTapGestures { tap ->
                                val mm = screenToMm(tap, pm, size.width.toFloat(), size.height.toFloat(), mapScale, mapOffset)
                                val hit = zones.indexOfLast {
                                    mm.first in it.minXmm..it.maxXmm && mm.second in it.minYmm..it.maxYmm
                                }
                                selectedIndex = if (hit >= 0 && hit != selectedIndex) hit else null
                            }
                        }
                        .pointerInput(drawMode, pm) {
                            if (drawMode) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStart = Offset(
                                            (offset.x - mapOffset.x) / mapScale,
                                            (offset.y - mapOffset.y) / mapScale,
                                        )
                                        dragCurrent = dragStart
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragCurrent = dragCurrent?.plus(delta / mapScale)
                                    },
                                    onDragEnd = {
                                        val start = dragStart
                                        val end = dragCurrent
                                        if (start != null && end != null) {
                                            val a = canvasToMm(start, pm, size.width.toFloat(), size.height.toFloat())
                                            val b = canvasToMm(end, pm, size.width.toFloat(), size.height.toFloat())
                                            // Ignore tiny drags (taps handled separately). ~15 cells.
                                            val enoughX = kotlin.math.abs(start.x - end.x) * pm.width / size.width > 15
                                            val enoughY = kotlin.math.abs(start.y - end.y) * pm.height / size.height > 15
                                            if (enoughX && enoughY) {
                                                zones = zones + MapZone.rect(a.first, a.second, b.first, b.second, activeKind)
                                                dirty = true
                                                saved = false
                                            }
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

                        val strokePx = (2.dp.toPx() / mapScale).coerceAtLeast(1f)

                        zones.forEachIndexed { i, zone ->
                            val p0 = mmToCanvas(zone.minXmm, zone.minYmm, pm, size.width, size.height)
                            val p1 = mmToCanvas(zone.maxXmm, zone.maxYmm, pm, size.width, size.height)
                            val left = minOf(p0.x, p1.x)
                            val top = minOf(p0.y, p1.y)
                            val w = kotlin.math.abs(p1.x - p0.x)
                            val h = kotlin.math.abs(p1.y - p0.y)
                            val c = colorFor(zone.kind)
                            drawRect(c.copy(alpha = 0.25f), Offset(left, top), Size(w, h))
                            drawRect(
                                color = if (i == selectedIndex) c else c.copy(alpha = 0.8f),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(if (i == selectedIndex) strokePx * 2f else strokePx),
                            )
                        }

                        val ds = dragStart
                        val dc = dragCurrent
                        if (drawMode && ds != null && dc != null) {
                            val c = colorFor(activeKind)
                            val left = minOf(ds.x, dc.x)
                            val top = minOf(ds.y, dc.y)
                            drawRect(c.copy(alpha = 0.15f), Offset(left, top), Size(maxOf(ds.x, dc.x) - left, maxOf(ds.y, dc.y) - top))
                            drawRect(
                                color = c,
                                topLeft = Offset(left, top),
                                size = Size(maxOf(ds.x, dc.x) - left, maxOf(ds.y, dc.y) - top),
                                style = Stroke(strokePx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / mapScale, 6f / mapScale))),
                            )
                        }
                    }
                }
            }

            // Controls: zone type + delete-selected.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = activeKind == ZoneKind.NO_GO,
                    onClick = { activeKind = ZoneKind.NO_GO },
                    label = { Text("No-go") },
                )
                FilterChip(
                    selected = activeKind == ZoneKind.NO_MOP,
                    onClick = { activeKind = ZoneKind.NO_MOP },
                    label = { Text("No-mop") },
                )
                Spacer(Modifier.weight(1f))
                if (selectedIndex != null) {
                    TextButton(onClick = {
                        val idx = selectedIndex!!
                        zones = zones.filterIndexed { i, _ -> i != idx }
                        selectedIndex = null
                        dirty = true
                        saved = false
                    }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Delete")
                    }
                }
            }

            Text(
                text = when {
                    saved -> "Saved to robot"
                    selectedIndex != null -> "Zone selected — tap Delete to remove, or tap elsewhere to deselect"
                    !drawMode -> "Pinch to zoom · pan the map · switch to draw mode to add zones"
                    else -> "Drag on the map to draw a ${if (activeKind == ZoneKind.NO_GO) "no-go" else "no-mop"} zone · tap a zone to select it"
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Robot-mm point -> canvas offset (matches [rememberMapBitmap]'s marker transform, incl. Y-flip). */
private fun DrawScope.mmToCanvas(xMm: Int, yMm: Int, pm: ParsedMap, w: Float, h: Float): Offset {
    val cellX = xMm / 50f - pm.pixelOffsetLeft
    val cellYBottom = yMm / 50f - pm.pixelOffsetTop
    val cellY = (pm.height - 1) - cellYBottom
    return Offset(cellX * (w / pm.width), cellY * (h / pm.height))
}

/** Canvas-space point (after pan/zoom already removed) -> robot-mm (x, y). */
private fun canvasToMm(canvas: Offset, pm: ParsedMap, w: Float, h: Float): Pair<Int, Int> {
    val cellX = canvas.x * pm.width / w
    val cellYTop = canvas.y * pm.height / h
    val xMm = ((cellX + pm.pixelOffsetLeft) * 50f).toInt()
    val cellYBottom = (pm.height - 1) - cellYTop
    val yMm = ((cellYBottom + pm.pixelOffsetTop) * 50f).toInt()
    return xMm to yMm
}

/** Raw screen point (before pan/zoom removed) -> robot-mm (x, y). */
private fun screenToMm(
    screen: Offset,
    pm: ParsedMap,
    w: Float,
    h: Float,
    mapScale: Float,
    mapOffset: Offset,
): Pair<Int, Int> {
    val canvas = Offset((screen.x - mapOffset.x) / mapScale, (screen.y - mapOffset.y) / mapScale)
    return canvasToMm(canvas, pm, w, h)
}
