package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MapTool
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.strings.MapStrings

/** The Manage Maps tiles shown, in official order; Furniture and Sequence only once Capabilities turns them on. */
internal fun manageMapTiles(supported: Set<MapTool>): List<MapTool> = MapTool.entries.filter { it in supported }

/** Map tools need a saved map; the tiles stay visible but disabled without one, as in the official app. */
internal fun mapToolsEnabled(floorMapsLoaded: Boolean, savedMaps: Int, mapLoaded: Boolean): Boolean =
    mapLoaded && !(floorMapsLoaded && savedMaps == 0)

/** "..." menu entries: Backup and Restore need the robot's support and a saved map; Switch Map only with several floors. */
internal data class ManageMapsMenu(val backup: Boolean, val restore: Boolean, val switchMap: Boolean)

internal fun manageMapsMenu(mapBackup: Boolean, current: FloorMap?, savedMaps: Int, multiLevel: Boolean?): ManageMapsMenu =
    ManageMapsMenu(
        backup = mapBackup && current != null,
        restore = mapBackup && current?.backupAt != null,
        switchMap = savedMaps > 1 || multiLevel == true,
    )

private fun toolIcon(tool: MapTool): ImageVector = when (tool) {
    MapTool.DIVIDE -> Icons.Default.VerticalSplit
    MapTool.COMBINE -> Icons.Default.AddBox
    MapTool.REMOVE -> Icons.Outlined.AutoFixNormal
    MapTool.NAME_ROOM -> Icons.Default.TextFields
    MapTool.NO_GO -> Icons.Default.Block
    MapTool.THRESHOLD -> Icons.Default.Straighten
    MapTool.FLOOR_TYPE -> Icons.Default.Texture
    MapTool.CARPET -> Icons.Default.GridOn
    MapTool.FURNITURE -> Icons.Default.Chair
    MapTool.SEQUENCE -> Icons.Default.FormatListNumbered
}

private fun tileLabel(tool: MapTool): String = when (tool) {
    MapTool.NO_GO -> MapStrings.TOOL_NO_GO
    else -> mapToolTitle(tool)
}

/**
 * Manage Maps (route VacuumManageMaps, also the device page's map button): the map with its name and
 * rename pencil, "..." for Backup / Restore / Switch Map, a rail with rotate and Map Details, and the
 * tool grid. A tile opens that tool's editor in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapEditScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    var toolName by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = toolName?.let { name -> MapTool.entries.find { it.name == name } }
    if (tool != null) {
        MapToolEditor(viewModel, tool, onClose = { toolName = null })
        return
    }

    val repository = viewModel.repository
    val parsedMap by repository.parsedMap.collectAsState()
    val floorMaps by repository.floorMaps.collectAsState()
    val floorMapsLoaded by repository.floorMapsLoaded.collectAsState()
    val currentFloorFlag by repository.currentFloorFlag.collectAsState()
    val status by repository.status.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val rotation by viewModel.mapRotation.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val display by rememberMapDisplay(viewModel)
    LaunchedEffect(Unit) {
        while (!repository.floorMapsLoaded.value) {
            runCatching { repository.fetchFloorMaps() }
            if (!repository.floorMapsLoaded.value) kotlinx.coroutines.delay(MAP_LIST_RETRY_MS)
        }
    }

    val current = floorMaps.find { it.mapFlag == currentFloorFlag } ?: floorMaps.singleOrNull()
    val menu = manageMapsMenu(caps.mapBackup, current, floorMaps.size, status.multiLevelEnabled)
    val manageBlocked = repository.mapManageBlockedReason(status.state)
    val toolsEnabled = mapToolsEnabled(floorMapsLoaded, floorMaps.size, parsedMap != null)

    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var switchOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            current?.name ?: MapStrings.MANAGE_MAPS,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (current != null) {
                            IconButton(onClick = { renaming = true }, enabled = manageBlocked == null && !busy) {
                                Icon(Icons.Default.Edit, MapStrings.RENAME_MAP, Modifier.size(20.dp))
                            }
                        }
                    }
                },
                actions = {
                    if (menu.backup || menu.restore || menu.switchMap) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, MapStrings.MORE) }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (menu.backup) {
                                    DropdownMenuItem(
                                        text = { Text(MapStrings.BACKUP) },
                                        enabled = manageBlocked == null && !busy,
                                        onClick = { menuOpen = false; backingUp = true },
                                    )
                                }
                                if (menu.backup || menu.restore) {
                                    DropdownMenuItem(
                                        text = { Text(MapStrings.RESTORE) },
                                        enabled = menu.restore && manageBlocked == null && !busy,
                                        onClick = { menuOpen = false; restoring = true },
                                    )
                                }
                                if (menu.switchMap) {
                                    DropdownMenuItem(
                                        text = { Text(MapStrings.SWITCH_MAP) },
                                        onClick = { menuOpen = false; switchOpen = true },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(MAP_HERO_BACKGROUND)) {
                val pm = parsedMap
                if (pm != null) {
                    ManageMapView(pm, display, rotation, Modifier.fillMaxSize())
                } else {
                    Text(
                        MapStrings.MAP_NOT_LOADED,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RailButton(Icons.AutoMirrored.Filled.RotateRight, MapStrings.ROTATE_90, MapStrings.ROTATE_MAP, enabled = pm != null) {
                        viewModel.rotateMap90()
                    }
                    RailButton(Icons.Default.Layers, MapStrings.MAP_DETAILS, MapStrings.MAP_DETAILS, enabled = true) {
                        detailsOpen = true
                    }
                }
            }
            ToolGrid(
                tools = manageMapTiles(caps.mapTools),
                enabled = toolsEnabled,
                note = if (!toolsEnabled && parsedMap != null) MapStrings.NEEDS_SAVED_MAP else null,
                onTool = { toolName = it.name },
            )
        }
    }

    if (renaming && current != null) {
        RenameMapDialog(
            floor = current,
            onConfirm = { name ->
                renaming = false
                viewModel.run("Map renamed") { it.renameFloorMap(current.mapFlag, name) }
            },
            onDismiss = { renaming = false },
        )
    }
    if (backingUp && current != null) {
        BackupMapDialog(
            floor = current,
            picture = parsedMap,
            onConfirm = {
                backingUp = false
                viewModel.run("Map backed up") { it.backupFloorMap(current.mapFlag) }
            },
            onDismiss = { backingUp = false },
        )
    }
    if (restoring && current != null) {
        var backupPicture by remember(current) { mutableStateOf<ParsedMap?>(null) }
        LaunchedEffect(current) {
            backupPicture = runCatching { repository.fetchSavedMap(current.mapFlag, backup = true) }.getOrNull()
        }
        RestoreBackupDialog(
            floor = current,
            picture = backupPicture,
            onConfirm = {
                restoring = false
                viewModel.run("Map restored from backup") { it.restoreFloorMapBackup(current.mapFlag) }
            },
            onDismiss = { restoring = false },
        )
    }
    if (detailsOpen) {
        ModalBottomSheet(onDismissRequest = { detailsOpen = false }) {
            MapDetailsSheet(viewModel, onClose = { detailsOpen = false })
        }
    }
    if (switchOpen) {
        ModalBottomSheet(
            onDismissRequest = { switchOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SwitchMapSheet(
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                busy = busy,
                loadSavedMap = { flag -> repository.fetchSavedMap(flag, backup = false) },
                onLoad = { flag ->
                    switchOpen = false
                    viewModel.run { it.switchFloor(flag) }
                },
            )
        }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick)
                .alpha(if (enabled) 1f else 0.38f),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The bottom card of tool tiles, four per row with a hairline between rows. */
@Composable
private fun ToolGrid(tools: List<MapTool>, enabled: Boolean, note: String?, onTool: (MapTool) -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            tools.chunked(4).forEachIndexed { row, chunk ->
                if (row > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth()) {
                    chunk.forEach { tool -> ToolGridTile(tool, enabled, Modifier.weight(1f)) { onTool(tool) } }
                    repeat(4 - chunk.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ToolGridTile(tool: MapTool, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(toolIcon(tool), contentDescription = null, modifier = Modifier.size(28.dp))
        Text(tileLabel(tool), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Switch Map: the saved maps as a two-column grid of cards; tapping one loads it. */
@Composable
private fun SwitchMapSheet(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    busy: Boolean,
    loadSavedMap: suspend (Int) -> ParsedMap,
    onLoad: (Int) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(MapStrings.SWITCH_MAP, style = MaterialTheme.typography.titleLarge)
        floorMaps.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { floor ->
                    var picture by remember(floor.mapFlag, floor.savedAt) { mutableStateOf<ParsedMap?>(null) }
                    LaunchedEffect(floor.mapFlag, floor.savedAt) { picture = runCatching { loadSavedMap(floor.mapFlag) }.getOrNull() }
                    val selected = floor.mapFlag == currentFloorFlag
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                picture?.let { SavedMapImage(it, Modifier.fillMaxSize()) }
                            }
                            Text(floor.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Button(onClick = { onLoad(floor.mapFlag) }, enabled = !busy && !selected, modifier = Modifier.fillMaxWidth()) {
                                Text(if (selected) MapStrings.LOADED else MapStrings.LOAD)
                            }
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/**
 * The map as Manage Maps shows it: fitted with a margin, turned by [rotationDegrees], no route, and
 * the Map Details choices applied (names, floor type, obstacles, floor plan or raw cells).
 */
@Composable
internal fun ManageMapView(map: ParsedMap, display: MapDisplay, rotationDegrees: Int, modifier: Modifier = Modifier) {
    val scale = remember(map) { supersampleFor(map) }
    val (floor, overlay) = rememberMapLayers(map, scale, display)
    val shapes = remember(map.floorKey(), display.homeLayout) { map.floorPlanShapes(display.homeLayout) }
    val bounds = remember(map.floorKey(), display.homeLayout) { map.contentBoundsNorm(display.homeLayout) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val roomIcons = rememberRoomIconPainters()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0E1A26))
    val rooms = remember(map.rooms, map.eraserZones) {
        map.rooms.filter { it.labelNormX != null && it.labelNormY != null && it.id !in map.erasedRoomIds }
    }
    val obstacleIcons = rememberObstacleIcons()
    Canvas(modifier.clipToBounds()) {
        val yaw = rotationDegrees.toFloat()
        val fit = fitScale(size.width, size.height, bounds.width * floor.width, bounds.height * floor.height, yaw, squash = 1f, freeTurning = false)
        val view = framedView(
            canvasW = size.width,
            canvasH = size.height,
            centerX = (bounds.left + bounds.right) / 2f * floor.width,
            centerY = (bounds.top + bounds.bottom) / 2f * floor.height,
            fit = fit,
            zoom = 1f,
            pan = Offset.Zero,
            yawDegrees = yaw,
            squash = 1f,
        )
        val quality = if (view.k >= 2f || (yaw % 90f == 0f && view.k >= 0.95f)) FilterQuality.None else FilterQuality.Low
        drawLayer(floor, view, quality)
        drawFloorPlanStrokes(shapes, view, scale)
        drawLayer(overlay, view, quality)
        drawRouteAndMarkers(map, view, scale, dark, route = false, pinLabels = measurer)
        if (display.obstacle) drawObstacles(map, view, floor.width / map.width.toFloat(), obstacleIcons)
        if (!display.roomNames) return@Canvas
        for (room in rooms) {
            val at = view.toScreen(room.labelNormX!! * floor.width, room.labelNormY!! * floor.height)
            val badge = 16.dp.toPx()
            val gap = 3.dp.toPx()
            val text = measurer.measure(
                room.name, labelStyle,
                overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                constraints = Constraints(maxWidth = 120.dp.roundToPx()),
            )
            val total = badge + gap + text.size.width
            val left = at.x - total / 2
            val centre = Offset(left + badge / 2, at.y)
            drawCircle(Color(0xFF14202B), radius = badge / 2, center = centre)
            val glyph = badge * 0.6f
            withTransform({ translate(left = centre.x - glyph / 2, top = centre.y - glyph / 2) }) {
                with(roomIcons.forTag(room.tag)) { draw(size = Size(glyph, glyph), colorFilter = ColorFilter.tint(Color.White)) }
            }
            drawText(text, topLeft = Offset(left + badge + gap, at.y - text.size.height / 2f))
        }
    }
}

private fun DrawScope.drawLayer(image: ImageBitmap, view: MapView, quality: FilterQuality) {
    withTransform({ transform(view.layerMatrix(0f)) }) {
        val size = IntSize(image.width, image.height)
        drawImage(image, IntOffset.Zero, size, IntOffset.Zero, size, filterQuality = quality)
    }
}
