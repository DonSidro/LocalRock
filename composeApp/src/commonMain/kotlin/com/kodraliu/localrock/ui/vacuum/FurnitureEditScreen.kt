package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kodraliu.localrock.shared.vacuum.*
import com.kodraliu.localrock.shared.vacuum.map.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun FurnitureEditScreen(viewModel: VacuumViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val repository = viewModel.repository
    val liveMap by repository.parsedMap.collectAsState()
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<FurnitureSnapshot?>(null) }
    var draft by remember { mutableStateOf<List<FurniturePlacement>>(emptyList()) }
    var selectedKey by remember { mutableStateOf<Int?>(null) }
    var nextKey by remember { mutableStateOf(-1) }
    var working by remember { mutableStateOf(true) }
    var requiresReload by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showCatalog by remember { mutableStateOf(false) }
    var showItems by remember { mutableStateOf(false) }
    var discardAction by remember { mutableStateOf<String?>(null) }
    val baseline = snapshot
    val map = baseline?.map
    val viewport = remember(map?.width, map?.height) { MapViewport() }
    val dirty = baseline != null && draft != baseline.placements
    val changedRemotely = baseline != null && liveMap?.let { !sameFurnitureBase(baseline.map, it) } == true
    val canEdit = baseline != null && !working && !requiresReload && !changedRemotely
    val selected = draft.firstOrNull { it.key == selectedKey }

    fun accept(fresh: FurnitureSnapshot) {
        snapshot = fresh; draft = fresh.placements; selectedKey = null; nextKey = -1; requiresReload = false
    }
    suspend fun load() {
        working = true; notice = null
        try { accept(repository.loadFurnitureEditor()) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { requiresReload = true; notice = e.message ?: "Couldn't load furniture. Try again." }
        finally { working = false }
    }
    LaunchedEffect(Unit) { load() }
    fun reload() { scope.launch { load() } }
    fun requestBack() {
        if (working) return
        if (dirty) discardAction = "back" else onBack()
    }
    BackHandler(enabled = true) { requestBack() }
    fun replace(item: FurniturePlacement) {
        if (canEdit && validFurniturePlacement(item)) {
            draft = draft.map { if (it.key == item.key) item else it }; notice = null
        }
    }
    fun delete(key: Int) {
        if (canEdit) {
            draft = draft.filterNot { it.key == key }
            selectedKey = null
            notice = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Furniture") },
            navigationIcon = {
                IconButton(onClick = ::requestBack, enabled = !working) {
                    Icon(Icons.Default.ArrowBack, "Back to Edit map")
                }
            },
            actions = {
                if (working) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                else {
                    IconButton(onClick = { if (dirty) discardAction = "reload" else reload() }) {
                        Icon(Icons.Default.Refresh, "Reload furniture")
                    }
                    if (dirty) Button(enabled = canEdit, modifier = Modifier.padding(end = 8.dp), onClick = {
                        val base = snapshot ?: return@Button
                        val edits = draft.toList()
                        working = true; requiresReload = true; notice = "Saving and checking the robot's map…"
                        scope.launch {
                            try {
                                accept(repository.saveFurnitureEdits(base, edits))
                                onSaved()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { notice = e.message ?: "Couldn't confirm the save. Reload the map to check." }
                            finally { working = false }
                        }
                    }) { Text("Save") }
                    else TextButton(onClick = ::requestBack, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
                }
            },
        )
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
          val dockLimit = maxHeight * .48f
          Column(Modifier.fillMaxSize()) {
            if (map == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (working) "Loading the robot's furniture…" else notice ?: "Furniture is unavailable.", textAlign = TextAlign.Center)
                    if (!working) TextButton(onClick = ::reload) { Text("Try again") }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    FurnitureCanvas(map, draft, selectedKey, viewport, canEdit, { selectedKey = it }, ::replace, ::delete, Modifier.fillMaxSize())
                    if (changedRemotely || requiresReload || notice != null) {
                        Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), shape = RoundedCornerShape(16.dp), tonalElevation = 4.dp) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (changedRemotely && !working) "The map changed. Reload before editing again."
                                    else notice ?: "Reload the map to check its current furniture.", style = MaterialTheme.typography.bodyMedium)
                                if (!working && (changedRemotely || requiresReload)) TextButton(onClick = {
                                    if (dirty) discardAction = "reload" else reload()
                                }) { Text("Reload") }
                            }
                        }
                    }
                }
                Surface(Modifier.heightIn(max = dockLimit), tonalElevation = 3.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = 136.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
                        if (selected == null) {
                            Text("Tap furniture to select it. Drag the map to pan; pinch to zoom.", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(onClick = { showCatalog = true }, enabled = canEdit, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Add, null); Text("Add furniture")
                                }
                                OutlinedButton(onClick = { showItems = true }, enabled = !working) { Text("Items (${draft.size})") }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(selected.kind?.label ?: "Furniture", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(onClick = { selectedKey = null }, enabled = !working) {
                                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Done")
                                }
                            }
                            Text("Tap × to delete. Drag the other corners to rotate, move or resize.",
                                style = MaterialTheme.typography.bodySmall)
                            Text("${(selected.widthMm / 10).roundToInt()} × ${(selected.depthMm / 10).roundToInt()} cm",
                                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
          }
        }
    }
    if (discardAction != null) AlertDialog(
        onDismissRequest = { discardAction = null }, title = { Text("Discard local changes?") },
        text = { Text(if (requiresReload) "Reload the robot's current furniture to check what was saved. Your local draft will be discarded."
            else "These furniture changes have not been saved to the robot.") },
        confirmButton = { TextButton(onClick = {
            val action = discardAction; discardAction = null
            if (action == "reload") reload() else onBack()
        }) { Text(if (discardAction == "reload") "Reload" else "Discard") } },
        dismissButton = { TextButton(onClick = { discardAction = null }) { Text("Keep editing") } },
    )
    if (showCatalog) ModalBottomSheet(onDismissRequest = { showCatalog = false }) {
        Text("Add furniture", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.headlineSmall)
        LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FURNITURE_CATALOG, key = { "${it.type}/${it.subtype}" }) { kind ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.clickable(enabled = canEdit) {
                        val (x, y) = map?.let { viewport.centerMm(it) } ?: return@clickable
                        val item = newFurniture(nextKey, kind,
                            x.coerceIn(kind.widthMm / 2 + 1, MAX_MAP_COORD_MM - kind.widthMm / 2 - 1),
                            y.coerceIn(kind.depthMm / 2 + 1, MAX_MAP_COORD_MM - kind.depthMm / 2 - 1))
                        if (validFurniturePlacement(item)) {
                            nextKey--; draft = draft + item; selectedKey = item.key; notice = null; showCatalog = false
                        }
                    }) {
                    Column(Modifier.padding(8.dp).heightIn(min = 116.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FurniturePreview(kind, Modifier.size(64.dp))
                        Text(kind.label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    if (showItems) ModalBottomSheet(onDismissRequest = { showItems = false }) {
        Text("Furniture on this map", Modifier.padding(20.dp), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(16.dp)) {
            if (draft.isEmpty()) item { Text("Use Add furniture to place your first item.") }
            items(draft, key = { it.key }) { item ->
                ListItem(headlineContent = { Text(item.kind?.label ?: "Furniture") },
                    supportingContent = { Text("${(item.widthMm / 10).roundToInt()} × ${(item.depthMm / 10).roundToInt()} cm") },
                    leadingContent = { item.kind?.let { FurniturePreview(it, Modifier.size(48.dp)) } },
                    modifier = Modifier.clickable {
                        selectedKey = item.key; showItems = false
                        map?.let { currentMap ->
                            val fitted = currentMap.fittedSize(viewport.viewSize)
                            val center = currentMap.mmToCanvas(item.corners.sumOf { it.x } / 4,
                                item.corners.sumOf { it.y } / 4, fitted.width, fitted.height)
                            viewport.offset = clampMapOffset(
                                Offset(viewport.viewSize.width / 2, viewport.viewSize.height / 2) - center * viewport.scale,
                                viewport.scale, fitted, viewport.viewSize)
                        }
                    })
            }
            if (map?.furniture?.any { it.isVisible && it.editablePlacement() == null } == true)
                item { Text("Some existing furniture is view-only.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun FurniturePreview(kind: FurnitureKind, modifier: Modifier) {
    val artwork = furniturePreviewArtwork(kind.type, kind.subtype) ?: return
    val image = imageResource(artwork.resource)
    Canvas(modifier) {
        drawIntoCanvas { drawFurniturePreviewImage(it, image, Rect(Offset.Zero, size), 3.dp.toPx()) }
    }
}

@Composable
private fun FurnitureCanvas(
    map: ParsedMap, draft: List<FurniturePlacement>, selectedKey: Int?, viewport: MapViewport,
    enabled: Boolean, onSelect: (Int?) -> Unit, onChange: (FurniturePlacement) -> Unit,
    onDelete: (Int) -> Unit, modifier: Modifier,
) {
    val bitmap = rememberMapBitmap(map, drawFurniture = false)
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val gap = with(density) { 24.dp.toPx() }
    val minSide = with(density) { 96.dp.toPx() }
    val handleRadius = with(density) { 24.dp.toPx() }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    val bounds = remember(map) { map.contentBoundsNorm() }
    var scale by viewport::scale
    var offset by viewport::offset
    LaunchedEffect(viewport, viewSize) {
        viewport.viewSize = viewSize
        if (viewSize.width > 0 && viewSize.height > 0 && viewport.framedFor != viewSize) {
            val frame = frameMap(bounds, map.fittedSize(viewSize), viewSize)
            scale = frame.first; offset = frame.second; viewport.framedFor = viewSize
        }
    }
    val currentDraft by rememberUpdatedState(draft)
    val currentSelected by rememberUpdatedState(selectedKey)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentSelect by rememberUpdatedState(onSelect)
    val currentChange by rememberUpdatedState(onChange)
    val currentDelete by rememberUpdatedState(onDelete)
    val fitted = map.fittedSize(viewSize)
    val selected = draft.firstOrNull { it.key == selectedKey }
    fun screenCorners(item: FurniturePlacement) = item.corners.map {
        map.mmToCanvas(it.x, it.y, fitted.width, fitted.height) * scale + offset
    }
    val frame = selected?.takeIf { fitted.width > 0f && fitted.height > 0f }?.let {
        furnitureSelectionCorners(screenCorners(it), gap, minSide)
    }
    Box(modifier.clipToBounds().onSizeChanged { viewSize = it.toSize() }
        .pointerInput(map, viewSize, gap, minSide, handleRadius) {
            val fitted = map.fittedSize(size.toSize())
            if (fitted.width <= 0f || fitted.height <= 0f) return@pointerInput
            fun mm(point: Offset) = map.canvasToMm((point - offset) / scale, fitted.width, fitted.height)
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startMm = mm(down.position)
                val startItem = currentDraft.firstOrNull { it.key == currentSelected }
                val startFrame = startItem?.let { item ->
                    furnitureSelectionCorners(item.corners.map {
                        map.mmToCanvas(it.x, it.y, fitted.width, fitted.height) * scale + offset
                    }, gap, minSide)
                }
                val handle = if (currentEnabled && startFrame != null)
                    furnitureHandleAt(down.position, startFrame, handleRadius) else null
                val action = handle ?: if (startItem?.contains(startMm.first, startMm.second) == true)
                    FurnitureHandle.MOVE else null
                var pastSlop = false; var multiTouch = false; var last = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) {
                        if (!pastSlop && !multiTouch && currentEnabled) {
                            if (handle == FurnitureHandle.DELETE && startItem != null && startFrame != null) {
                                val up = event.changes.firstOrNull { it.id == down.id }
                                if (up != null && furnitureHandleAt(up.position, startFrame, handleRadius) == handle)
                                    currentDelete(startItem.key)
                            } else if (handle == null) {
                                val point = mm(down.position)
                                val slop = 12.dp.toPx() / scale * map.width / fitted.width * MM_PER_CELL
                                val hit = currentDraft.lastOrNull { item ->
                                    item.contains(point.first, point.second) || item.corners.indices.any { i ->
                                        val a = item.corners[i]; val b = item.corners[(i + 1) % 4]
                                        distanceToSegmentMm(point.first, point.second, a.x, a.y, b.x, b.y) <= slop
                                    }
                                }
                                currentSelect(hit?.key)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        break
                    }
                    if (pressed.size >= 2) {
                        multiTouch = true
                        val transformed = zoomMap(scale, offset, event.calculateCentroid(), event.calculatePan(),
                            event.calculateZoom(), fitted, size.toSize())
                        scale = transformed.first; offset = transformed.second
                        event.changes.forEach { it.consume() }
                        continue
                    }
                    // Lifting one finger after pinching must not resume an old furniture drag.
                    if (multiTouch) { event.changes.forEach { it.consume() }; continue }
                    val change = pressed.first()
                    if (!pastSlop && (change.position - down.position).getDistance() < viewConfiguration.touchSlop) continue
                    pastSlop = true; change.consume()
                    if (startItem != null && action != null && currentEnabled) {
                        val point = mm(change.position)
                        val dx = point.first - startMm.first; val dy = point.second - startMm.second
                        val updated = when (action) {
                            FurnitureHandle.MOVE -> startItem.moved(dx, dy)
                            FurnitureHandle.ROTATE -> startItem.rotatedFromHandle(startMm, point)
                            FurnitureHandle.RESIZE -> startItem.resizedFromHandle(dx, dy)
                            FurnitureHandle.DELETE -> startItem
                        }
                        if (action != FurnitureHandle.DELETE && validFurniturePlacement(updated)) currentChange(updated)
                    } else offset = clampMapOffset(offset + change.position - last, scale, fitted, size.toSize())
                    last = change.position
                }
            }
        }) {
      Canvas(Modifier.fillMaxSize().semantics {
          contentDescription = "Furniture map. Select furniture on the map or in Items. Its four corner controls delete, rotate, move and resize."
      }) {
        val fittedCanvas = map.fittedSize(size)
        withTransform({ translate(offset.x, offset.y); scale(scale, scale, Offset.Zero) }) {
            drawImage(bitmap, dstSize = IntSize(fittedCanvas.width.roundToInt(), fittedCanvas.height.roundToInt()), filterQuality = FilterQuality.None)
            drawIntoCanvas { canvas ->
                map.furniture.filter { it.isVisible && it.editablePlacement() == null }.forEach { item ->
                    drawFurnitureSymbol(canvas, item.cornersMm.map { map.mmToCanvas(it.x, it.y, fittedCanvas.width, fittedCanvas.height) },
                        item.type, item.subtype, dark, 1.dp.toPx() / scale)
                }
                draft.forEach { item ->
                    drawFurnitureSymbol(canvas, item.corners.map { map.mmToCanvas(it.x, it.y, fittedCanvas.width, fittedCanvas.height) },
                        item.type, item.subtype, dark, 1.dp.toPx() / scale)
                }
            }
        }
        frame?.let { points ->
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, primary.copy(alpha = if (enabled) 1f else .4f),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }
      }
      if (selected != null && frame != null) {
          FurnitureHandle.entries.forEach { handle ->
              FurnitureCornerControl(handle, frame[handle.corner], selected, enabled, onChange, onDelete)
          }
      }
    }
}

/** Semantics expose precise steps to VoiceOver/TalkBack; touch uses the shared map gesture above. */
@Composable
private fun FurnitureCornerControl(handle: FurnitureHandle, center: Offset, item: FurniturePlacement,
    enabled: Boolean, onChange: (FurniturePlacement) -> Unit, onDelete: (Int) -> Unit) {
    val radius = with(LocalDensity.current) { 24.dp.toPx() }
    val label = when (handle) {
        FurnitureHandle.DELETE -> "Delete furniture"
        FurnitureHandle.ROTATE -> "Rotate furniture"
        FurnitureHandle.MOVE -> "Move furniture"
        FurnitureHandle.RESIZE -> "Resize furniture"
    }
    val icon = when (handle) {
        FurnitureHandle.DELETE -> Icons.Default.Close
        FurnitureHandle.ROTATE -> Icons.Default.RotateRight
        FurnitureHandle.MOVE -> Icons.Default.OpenWith
        FurnitureHandle.RESIZE -> Icons.Default.OpenInFull
    }
    fun update(value: FurniturePlacement): Boolean {
        if (!enabled || !validFurniturePlacement(value)) return false
        onChange(value)
        return true
    }
    fun resize(width: Double, height: Double) = update(item.resized(maxOf(101.0, width), maxOf(101.0, height)))
    Box(Modifier.absoluteOffset { IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()) }
        .size(48.dp).semantics {
            role = Role.Button
            contentDescription = label
            stateDescription = "${(item.widthMm / 10).roundToInt()} by ${(item.depthMm / 10).roundToInt()} centimeters"
            if (!enabled) disabled()
            else when (handle) {
                FurnitureHandle.DELETE -> onClick { onDelete(item.key); true }
                FurnitureHandle.ROTATE -> {
                    onClick("Rotate clockwise 15 degrees") { update(item.rotated(-15.0)) }
                    customActions = listOf(
                        CustomAccessibilityAction("Rotate clockwise 15 degrees") { update(item.rotated(-15.0)) },
                        CustomAccessibilityAction("Rotate counterclockwise 15 degrees") { update(item.rotated(15.0)) })
                }
                FurnitureHandle.MOVE -> {
                    customActions = listOf(
                        CustomAccessibilityAction("Move left 5 cm") { update(item.moved(-50, 0)) },
                        CustomAccessibilityAction("Move right 5 cm") { update(item.moved(50, 0)) },
                        CustomAccessibilityAction("Move up 5 cm") { update(item.moved(0, 50)) },
                        CustomAccessibilityAction("Move down 5 cm") { update(item.moved(0, -50)) })
                }
                FurnitureHandle.RESIZE -> {
                    customActions = listOf(
                        CustomAccessibilityAction("Increase width 5 cm") { resize(item.widthMm + 50, item.depthMm) },
                        CustomAccessibilityAction("Reduce width 5 cm") { resize(item.widthMm - 50, item.depthMm) },
                        CustomAccessibilityAction("Increase height 5 cm") { resize(item.widthMm, item.depthMm + 50) },
                        CustomAccessibilityAction("Reduce height 5 cm") { resize(item.widthMm, item.depthMm - 50) })
                }
            }
        }, contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape,
            color = (if (handle == FurnitureHandle.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .copy(alpha = if (enabled) 1f else .4f),
            contentColor = if (handle == FurnitureHandle.DELETE) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 2.dp, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
        }
    }
}
