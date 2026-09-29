package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import com.kodraliu.localrock.ui.exceptBottom
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.kodraliu.localrock.shared.vacuum.MAX_MAP_COORD_MM
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.MAX_RESTRICTION_VERTICES
import com.kodraliu.localrock.shared.vacuum.mapIndexBlocker
import com.kodraliu.localrock.shared.vacuum.restrictionSaveBlocker
import com.kodraliu.localrock.shared.vacuum.restrictionsMatch
import com.kodraliu.localrock.shared.vacuum.thresholdStrip
import com.kodraliu.localrock.shared.vacuum.saveMapIndex
import com.kodraliu.localrock.shared.vacuum.restrictionVertexCount
import com.kodraliu.localrock.shared.vacuum.map.FloorMaterial
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import com.kodraliu.localrock.shared.vacuum.map.roomAtNorm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

private val NO_GO_COLOR = Color(0xfff44336)
private val NO_MOP_COLOR = Color(0xff2196f3)
private val WALL_COLOR = Color(0xffe53935)
private val SPLIT_COLOR = Color(0xffff9800)
// Same brown as the map renderer's carpet stroke; ~4.9:1 on the dark surface, ~3.3:1 on white.
private val CARPET_COLOR = Color(0xffa1887f)
private val THRESHOLD_COLOR = Color(0xffffb300)

/** Shapes smaller than this (either side of a zone, or a line) are treated as accidental drags. */
private const val MIN_SHAPE_MM = 200

private enum class EditMode { RESTRICTIONS, ROOMS }
private enum class Tool { NO_GO, WALL, CARPET, THRESHOLD }

private sealed interface Selection {
    data class Zone(val index: Int) : Selection
    data class Wall(val index: Int) : Selection
}

private sealed interface RoomAction {
    data object None : RoomAction
    data class Split(val segment: Int) : RoomAction
    data class Merge(val segment: Int) : RoomAction
}

private data class Notice(val text: String, val isError: Boolean)

private data class PendingSplit(val segment: Int, val a: Pair<Int, Int>, val b: Pair<Int, Int>)
private data class PendingMerge(val segmentA: Int, val segmentB: Int)

/** The floor choices offered, as (label, material, wood direction). */
private data class FloorChoice(val label: String, val material: FloorMaterial, val direction: Int?)

private val FLOOR_CHOICES = listOf(
    FloorChoice("Default", FloorMaterial.DEFAULT, null),
    FloorChoice("Wood, horizontal", FloorMaterial.WOOD, 0),
    FloorChoice("Wood, vertical", FloorMaterial.WOOD, 90),
    FloorChoice("Tile", FloorMaterial.TILE, null),
)

private fun zoneColor(kind: ZoneKind) = when (kind) {
    ZoneKind.NO_GO -> NO_GO_COLOR
    ZoneKind.NO_MOP -> NO_MOP_COLOR
    ZoneKind.CARPET -> CARPET_COLOR
    ZoneKind.THRESHOLD -> THRESHOLD_COLOR
}

/** Zones that count towards save_map's vertex limit (carpets and thresholds have their own commands). */
private fun List<MapZone>.restrictionZones() = filter { it.kind == ZoneKind.NO_GO || it.kind == ZoneKind.NO_MOP }

/**
 * Map editor with two modes.
 *
 * Zones & walls: no-go zones, virtual walls, carpet areas and thresholds are edited locally and
 * saved together. Each kind is written as a complete set (save_map, set_carpet_area,
 * app_set_smart_door_sill), so what the user removes is removed from the robot. Existing shapes
 * keep their original corners, so anything the user does not touch is sent back unchanged.
 *
 * Rooms: rename, floor type, split and merge. Each one is sent immediately and only reported as
 * done once the robot's next map (or room table) shows it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MapEditScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    val parsedMap by repository.parsedMap.collectAsState()
    val status by repository.status.collectAsState()
    val floorMaps by repository.floorMaps.collectAsState()
    val floorMapsLoaded by repository.floorMapsLoaded.collectAsState()
    // The list request can be lost (e.g. sent while MQTT is reconnecting); keep asking until the
    // robot answers, since saving needs it.
    LaunchedEffect(Unit) {
        while (!repository.floorMapsLoaded.value) {
            runCatching { repository.fetchFloorMaps() }
            if (!repository.floorMapsLoaded.value) delay(MAP_LIST_RETRY_MS)
        }
    }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(EditMode.RESTRICTIONS) }
    val viewport = remember(parsedMap?.width, parsedMap?.height) { MapViewport() }

    var zones by remember { mutableStateOf<List<MapZone>>(emptyList()) }
    var walls by remember { mutableStateOf<List<VirtualWall>>(emptyList()) }
    var dirty by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Selection?>(null) }

    var selectedRoom by remember { mutableStateOf<Int?>(null) }
    var showRoomSheet by remember { mutableStateOf(false) }
    var roomAction by remember { mutableStateOf<RoomAction>(RoomAction.None) }
    var pendingSplit by remember { mutableStateOf<PendingSplit?>(null) }
    var pendingMerge by remember { mutableStateOf<PendingMerge?>(null) }

    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Follow the robot's map until the user starts editing; after that local edits own the state
    // so the periodic map poll can't wipe work in progress.
    LaunchedEffect(parsedMap, dirty) {
        val pm = parsedMap
        if (!dirty && pm != null) {
            zones = pm.noGoZones + pm.noMopZones + pm.carpetAreas + pm.thresholds
            walls = pm.virtualWalls
            selection = null
        }
    }

    val pm = parsedMap
    val blockedReason = repository.editBlockedReason(pm, status.state)
    val mapIndex = saveMapIndex(floorMaps)
    // Carpets and thresholds only need the map index; no-go zones and walls also need save_map to
    // be safe on this map.
    // Until the robot's map list arrives nothing is blocked for drawing; only Save waits for it.
    val indexBlocker = when {
        !floorMapsLoaded -> null
        floorMaps.isEmpty() -> "The robot has no saved map yet. Map edits need a saved map."
        else -> mapIndexBlocker(mapIndex)
    }
    val saveBlocker = if (floorMapsLoaded) pm?.let { restrictionSaveBlocker(it, mapIndex) } else null
    val restrictionsChanged = pm != null &&
        !restrictionsMatch(pm, zones.filter { it.kind == ZoneKind.NO_GO }, pm.noMopZones, walls)
    val vertices = restrictionVertexCount(walls.size, zones.restrictionZones().size)

    LaunchedEffect(notice) {
        if (notice?.isError == false) {
            delay(NOTICE_VISIBLE_MS)
            notice = null
        }
    }

    fun runEdit(successText: String, block: suspend () -> String?) {
        scope.launch {
            working = true
            notice = null
            try {
                notice = Notice(block() ?: successText, isError = false)
            } catch (e: Throwable) {
                notice = Notice(e.message ?: "Something went wrong", isError = true)
            } finally {
                working = false
            }
        }
    }

    val requestBack = {
        if (dirty) showDiscardConfirm = true else onBack()
    }
    // System back and the back gesture must not silently drop unsaved zone and wall edits.
    BackHandler(enabled = dirty && !showDiscardConfirm) { showDiscardConfirm = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit map") },
                navigationIcon = {
                    IconButton(onClick = requestBack) { Icon(Icons.Default.Close, "Close editor") }
                },
                actions = {
                    if (working) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else if (mode == EditMode.RESTRICTIONS && dirty) {
                        // The one prominent action, on the trailing edge.
                        Button(
                            onClick = { showSaveConfirm = true },
                            enabled = blockedReason == null && floorMapsLoaded && indexBlocker == null &&
                                (saveBlocker == null || !restrictionsChanged),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        // Edge to edge: the dock runs to the bottom of the screen and pads itself for the
        // navigation bar, so no strip of background shows under it.
        Column(Modifier.fillMaxSize().padding(padding.exceptBottom())) {
            if (pm == null) {
                MapNotLoaded()
                return@Column
            }

            val editable = blockedReason == null && !working
            fun toolEnabled(t: Tool) = editable && when (t) {
                Tool.NO_GO, Tool.WALL -> saveBlocker == null
                Tool.CARPET, Tool.THRESHOLD -> indexBlocker == null
            }
            val deleteEnabled = editable && when (val sel = selection) {
                is Selection.Wall -> saveBlocker == null
                is Selection.Zone -> when (zones.getOrNull(sel.index)?.kind) {
                    ZoneKind.NO_GO -> saveBlocker == null
                    ZoneKind.CARPET, ZoneKind.THRESHOLD -> indexBlocker == null
                    ZoneKind.NO_MOP, null -> false
                }
                null -> false
            }

            fun addShape(t: Tool) {
                val (cx, cy) = viewport.centerMm(pm) ?: return
                // Sized to what is on screen so the handles never crowd together when zoomed out.
                val side = viewport.newShapeSizeMm(pm)
                val half = side / 2
                val restrictionLimitHit = when (t) {
                    Tool.NO_GO -> restrictionVertexCount(walls.size, zones.restrictionZones().size + 1) > MAX_RESTRICTION_VERTICES
                    Tool.WALL -> restrictionVertexCount(walls.size + 1, zones.restrictionZones().size) > MAX_RESTRICTION_VERTICES
                    else -> false
                }
                if (restrictionLimitHit) {
                    notice = Notice("The robot stores at most $MAX_RESTRICTION_VERTICES zone and wall points. Remove one first.", true)
                    return
                }
                // New shapes start in the middle of what is on screen, already selected, so the
                // next touch adjusts them (the official app works the same way).
                when (t) {
                    Tool.NO_GO -> {
                        zones = zones + MapZone.rect(cx - half, cy - half, cx + half, cy + half, ZoneKind.NO_GO)
                        selection = Selection.Zone(zones.lastIndex)
                    }
                    Tool.CARPET -> {
                        zones = zones + MapZone.rect(cx - half * 8 / 5, cy - half, cx + half * 8 / 5, cy + half, ZoneKind.CARPET)
                        selection = Selection.Zone(zones.lastIndex)
                    }
                    Tool.THRESHOLD -> {
                        // A doorway is roughly 0.9 m whatever the zoom.
                        zones = zones + thresholdStrip(cx - 450 to cy, cx + 450 to cy)
                        selection = Selection.Zone(zones.lastIndex)
                    }
                    Tool.WALL -> {
                        walls = walls + VirtualWall(cx - half * 6 / 5, cy, cx + half * 6 / 5, cy)
                        selection = Selection.Wall(walls.lastIndex)
                    }
                }
                dirty = true
                notice = null
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                EditorCanvas(
                    map = pm,
                    mode = mode,
                    zones = zones,
                    walls = walls,
                    selection = selection,
                    selectedRoom = selectedRoom,
                    roomAction = roomAction,
                    viewport = viewport,
                    // Only a room split is drawn by dragging; shapes are added from the dock.
                    drawEnabled = mode == EditMode.ROOMS && editable && roomAction is RoomAction.Split,
                    onLineDrawn = { a, b ->
                        val action = roomAction
                        if (action is RoomAction.Split) pendingSplit = PendingSplit(action.segment, a, b)
                    },
                    onSelectShape = { selection = it },
                    canModifySelection = deleteEnabled,
                    onZoneChanged = { i, z ->
                        if (z.fitsMap()) {
                            zones = zones.mapIndexed { j, old -> if (j == i) z else old }
                            dirty = true
                            notice = null
                        }
                    },
                    onWallChanged = { i, wl ->
                        if (wl.fitsMap()) {
                            walls = walls.mapIndexed { j, old -> if (j == i) wl else old }
                            dirty = true
                            notice = null
                        }
                    },
                    onRoomTap = { segment ->
                        val action = roomAction
                        when {
                            segment == null -> Unit
                            action is RoomAction.Merge && segment != action.segment ->
                                pendingMerge = PendingMerge(action.segment, segment)
                            action is RoomAction.None -> {
                                selectedRoom = segment
                                showRoomSheet = true
                            }
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Floating control layer over the map: mode switch and any warning at the top,
                // results at the bottom.
                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeSwitch(
                        mode = mode,
                        roomsEnabled = !working && !dirty,
                        enabled = !working,
                        onMode = { m ->
                            mode = m
                            selection = null
                            roomAction = RoomAction.None
                        },
                    )
                    val warning = blockedReason ?: if (mode == EditMode.RESTRICTIONS) (indexBlocker ?: saveBlocker) else null
                    if (warning != null) {
                        WarningCard(warning)
                    } else if (!floorMapsLoaded && mode == EditMode.RESTRICTIONS) {
                        WarningCard("Checking the robot's saved maps. You can edit now; saving waits for this.", isError = false)
                    }
                }
                notice?.let { n ->
                    NoticePill(
                        notice = n,
                        onDismiss = { notice = null },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }

            EditorDock {
                when (mode) {
                    EditMode.RESTRICTIONS -> {
                        val sel = selection
                        if (sel == null) {
                            ToolDock(toolEnabled = ::toolEnabled, onAdd = ::addShape, vertices = vertices)
                        } else {
                            val selectedZone = (sel as? Selection.Zone)?.let { zones.getOrNull(it.index) }
                            val kind = selectedZone?.kind
                            ShapeBar(
                                kind = kind,
                                round = selectedZone?.isRoundCarpet == true,
                                canEdit = deleteEnabled,
                                onDelete = {
                                    when (sel) {
                                        is Selection.Zone -> zones = zones.filterIndexed { i, _ -> i != sel.index }
                                        is Selection.Wall -> walls = walls.filterIndexed { i, _ -> i != sel.index }
                                    }
                                    selection = null
                                    dirty = true
                                },
                                onDone = { selection = null },
                            )
                        }
                    }
                    EditMode.ROOMS -> RoomDock(
                        action = roomAction,
                        roomName = { id -> pm.rooms.find { it.id == id }?.name ?: "Room $id" },
                        onCancel = { roomAction = RoomAction.None },
                    )
                }
            }
        }
    }

    if (showSaveConfirm) {
        val noGo = zones.count { it.kind == ZoneKind.NO_GO }
        val carpets = zones.count { it.kind == ZoneKind.CARPET }
        val thresholds = zones.count { it.kind == ZoneKind.THRESHOLD }
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Save to robot?") },
            text = {
                Text(
                    "The robot will keep exactly these: $noGo no-go zone(s), ${walls.size} wall(s), " +
                        "$carpets carpet area(s) and $thresholds threshold(s). Anything removed here is " +
                        "removed from the robot."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    val noGoZones = zones.filter { it.kind == ZoneKind.NO_GO }
                    val carpetAreas = zones.filter { it.kind == ZoneKind.CARPET }
                    val thresholdStrips = zones.filter { it.kind == ZoneKind.THRESHOLD }
                    val savedWalls = walls
                    runEdit("Saved. The robot's map shows the changes.") {
                        repository.saveMapEdits(noGoZones, savedWalls, carpetAreas, thresholdStrips)
                        dirty = false
                        selection = null
                        null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your map changes have not been saved to the robot.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") } },
        )
    }

    val room = selectedRoom
    if (showRoomSheet && room != null && pm != null) {
        ModalBottomSheet(onDismissRequest = { showRoomSheet = false }) {
            RoomSheet(
                map = pm,
                segment = room,
                working = working,
                enabled = blockedReason == null,
                onRename = { name ->
                    runEdit("Room renamed.") {
                        viewModel.renameRoom(room, name)
                        null
                    }
                },
                onFloor = { choice ->
                    runEdit("Floor type set. The robot's map shows the change.") {
                        val confirmed = repository.setFloorMaterial(room, choice.material, choice.direction)
                        if (confirmed) null
                        else "Floor type sent. This robot's map doesn't report floor types, so LocalRock can't confirm it."
                    }
                },
                onSplit = {
                    showRoomSheet = false
                    roomAction = RoomAction.Split(room)
                    notice = null
                },
                onMerge = {
                    showRoomSheet = false
                    roomAction = RoomAction.Merge(room)
                    notice = null
                },
            )
        }
    }

    pendingSplit?.let { split ->
        val name = pm?.rooms?.find { it.id == split.segment }?.name ?: "this room"
        AlertDialog(
            onDismissRequest = { pendingSplit = null },
            title = { Text("Split $name?") },
            text = {
                Text(
                    "The robot divides the room along the line you drew. The line must cross the room from wall to " +
                        "wall. Both parts get new room numbers, so give them names afterwards and check any " +
                        "schedules that include this room."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingSplit = null
                    roomAction = RoomAction.None
                    runEdit("Room split. The robot's map shows the new rooms. Name them from the room list.") {
                        repository.splitRoom(split.segment, split.a.first, split.a.second, split.b.first, split.b.second)
                        null
                    }
                }) { Text("Split") }
            },
            dismissButton = { TextButton(onClick = { pendingSplit = null }) { Text("Cancel") } },
        )
    }

    pendingMerge?.let { merge ->
        val nameA = pm?.rooms?.find { it.id == merge.segmentA }?.name ?: "Room ${merge.segmentA}"
        val nameB = pm?.rooms?.find { it.id == merge.segmentB }?.name ?: "Room ${merge.segmentB}"
        AlertDialog(
            onDismissRequest = { pendingMerge = null },
            title = { Text("Merge rooms?") },
            text = {
                Text(
                    "$nameA and $nameB become one room. They must be next to each other, or the robot " +
                        "refuses. The merged room may lose its name, and schedules that include either room " +
                        "may need updating."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMerge = null
                    roomAction = RoomAction.None
                    selectedRoom = null
                    runEdit("Rooms merged. The robot's map shows one room.") {
                        repository.mergeRooms(merge.segmentA, merge.segmentB)
                        null
                    }
                }) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = { pendingMerge = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EditorCanvas(
    map: ParsedMap,
    mode: EditMode,
    zones: List<MapZone>,
    walls: List<VirtualWall>,
    selection: Selection?,
    selectedRoom: Int?,
    roomAction: RoomAction,
    viewport: MapViewport,
    drawEnabled: Boolean,
    onLineDrawn: (Pair<Int, Int>, Pair<Int, Int>) -> Unit,
    onSelectShape: (Selection?) -> Unit,
    onRoomTap: (Int?) -> Unit,
    canModifySelection: Boolean,
    onZoneChanged: (Int, MapZone) -> Unit,
    onWallChanged: (Int, VirtualWall) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberMapBitmap(map, drawRestrictions = false)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onPrimary)
    val labelBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val selectedBg = MaterialTheme.colorScheme.primary

    var scale by viewport::scale
    var offset by viewport::offset

    // Open framed on the floor plan rather than the mostly empty grid, like the main map does.
    // Done once per map size so later map polls don't undo the user's own zoom.
    val bounds = remember(map.width, map.height) { map.contentBoundsNorm() }
    var canvasSize by viewport::canvasSize
    var framed by viewport::framed
    LaunchedEffect(bounds, canvasSize) {
        if (framed || !bounds.isUsable || canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val fitScale = minOf(1f / bounds.width, 1f / bounds.height).coerceIn(1f, 8f)
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()
        val cx = (bounds.left + bounds.right) / 2f
        val cy = (bounds.top + bounds.bottom) / 2f
        offset = Offset(
            (cw * (0.5f - fitScale * cx)).coerceIn(-cw * (fitScale - 1f), 0f),
            (ch * (0.5f - fitScale * cy)).coerceIn(-ch * (fitScale - 1f), 0f),
        )
        scale = fitScale
        framed = true
    }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    // Gesture lambdas outlive recompositions; read the latest values through these.
    val currentZones by rememberUpdatedState(zones)
    val currentWalls by rememberUpdatedState(walls)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnLineDrawn by rememberUpdatedState(onLineDrawn)
    val currentOnSelect by rememberUpdatedState(onSelectShape)
    val currentOnRoomTap by rememberUpdatedState(onRoomTap)
    val currentDrawEnabled by rememberUpdatedState(drawEnabled)
    val currentCanModify by rememberUpdatedState(canModifySelection)
    val currentOnZoneChanged by rememberUpdatedState(onZoneChanged)
    val currentOnWallChanged by rememberUpdatedState(onWallChanged)
    // White dots with a coloured ring read on every room tint in both themes.
    val handleColor = Color.White


    Box(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(map.width.toFloat() / map.height.toFloat())
                .onSizeChanged { canvasSize = it }
                .pointerInput(map, mode) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val content = (tap - offset) / scale
                        if (mode == EditMode.ROOMS) {
                            currentOnRoomTap(map.roomAtNorm(content.x / w, content.y / h)?.id)
                            return@detectTapGestures
                        }
                        val (xMm, yMm) = map.canvasToMm(content, w, h)
                        // 24dp of finger slop, converted to mm at the current zoom.
                        val slopMm = 24.dp.toPx() / scale * (map.width / w) * MM_PER_CELL
                        val wallHit = currentWalls.indexOfLast {
                            distanceToSegmentMm(xMm, yMm, it.x0, it.y0, it.x1, it.y1) <= slopMm
                        }
                        val hit: Selection? = if (wallHit >= 0) {
                            Selection.Wall(wallHit)
                        } else {
                            currentZones.indexOfLast { z ->
                                z.contains(xMm, yMm) ||
                                    // Threshold strips are ~2 map pixels wide; accept taps near the centre line.
                                    (z.kind == ZoneKind.THRESHOLD && distanceToSegmentMm(
                                        xMm, yMm,
                                        (z.x0 + z.x3) / 2, (z.y0 + z.y3) / 2,
                                        (z.x1 + z.x2) / 2, (z.y1 + z.y2) / 2,
                                    ) <= slopMm)
                            }.takeIf { it >= 0 }?.let { Selection.Zone(it) }
                        }
                        currentOnSelect(if (hit == currentSelection) null else hit)
                    }
                }
                .pointerInput(map, mode, drawEnabled) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    fun clampToMap(p: Offset) = Offset(p.x.coerceIn(0f, w), p.y.coerceIn(0f, h))
                    fun contentOf(p: Offset) = (p - offset) / scale
                    val handleRadius = HANDLE_TOUCH_RADIUS.toPx()
                    val rotateDistance = ROTATE_HANDLE_DISTANCE.toPx()

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sel = currentSelection
                        // What this touch does is decided where it lands: on the selected shape's
                        // handles or body it edits that shape, otherwise it draws or pans.
                        val target: DragTarget? =
                            if (mode == EditMode.RESTRICTIONS && sel != null && currentCanModify) {
                                hitDragTarget(
                                    sel, currentZones, currentWalls, map, w, h, scale, offset,
                                    down.position, handleRadius, rotateDistance,
                                )
                            } else null
                        var gesture = when {
                            target != null -> Gesture.EDIT
                            currentDrawEnabled -> Gesture.DRAW
                            else -> Gesture.PAN
                        }
                        val startZone = (sel as? Selection.Zone)?.let { currentZones.getOrNull(it.index) }
                        val startWall = (sel as? Selection.Wall)?.let { currentWalls.getOrNull(it.index) }
                        val startMm = map.canvasToMm(clampToMap(contentOf(down.position)), w, h)
                        var pastSlop = false
                        var last = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (gesture == Gesture.DRAW && pastSlop) {
                                    val sd = dragStart
                                    val ed = dragEnd
                                    if (sd != null && ed != null) {
                                        val a = map.canvasToMm(sd, w, h)
                                        val b = map.canvasToMm(ed, w, h)
                                        val dx = abs(a.first - b.first)
                                        val dy = abs(a.second - b.second)
                                        if (hypot(dx.toFloat(), dy.toFloat()) >= MIN_SHAPE_MM) currentOnLineDrawn(a, b)
                                    }
                                }
                                dragStart = null
                                dragEnd = null
                                break
                            }
                            if (pressed.size >= 2 || gesture == Gesture.ZOOM) {
                                // A second finger always means zoom/pan; an unfinished drawing is dropped.
                                gesture = Gesture.ZOOM
                                dragStart = null
                                dragEnd = null
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val newScale = (scale * zoom).coerceIn(1f, 8f)
                                val moved = centroid - (centroid - offset) * (newScale / scale) + pan
                                offset = Offset(
                                    moved.x.coerceIn(-w * (newScale - 1f), 0f),
                                    moved.y.coerceIn(-h * (newScale - 1f), 0f),
                                )
                                scale = newScale
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            val change = pressed.first()
                            val pos = change.position
                            if (!pastSlop) {
                                if ((pos - down.position).getDistance() < viewConfiguration.touchSlop) continue
                                pastSlop = true
                                if (gesture == Gesture.DRAW) dragStart = clampToMap(contentOf(down.position))
                            }
                            change.consume()
                            when (gesture) {
                                Gesture.PAN -> {
                                    val moved = offset + (pos - last)
                                    offset = Offset(
                                        moved.x.coerceIn(-w * (scale - 1f), 0f),
                                        moved.y.coerceIn(-h * (scale - 1f), 0f),
                                    )
                                }
                                Gesture.DRAW -> dragEnd = clampToMap(contentOf(pos))
                                Gesture.EDIT -> {
                                    val nowMm = map.canvasToMm(clampToMap(contentOf(pos)), w, h)
                                    applyEdit(
                                        target!!, sel!!, startZone, startWall, startMm, nowMm,
                                        onZone = { i, z -> currentOnZoneChanged(i, z) },
                                        onWall = { i, wl -> currentOnWallChanged(i, wl) },
                                    )
                                }
                                Gesture.ZOOM -> Unit
                            }
                            last = pos
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(w.toInt(), h.toInt()),
                    filterQuality = FilterQuality.None,
                )
                val stroke = (2.dp.toPx() / scale).coerceAtLeast(1f)
                val dimmed = mode == EditMode.ROOMS

                zones.forEachIndexed { i, zone ->
                    val path = Path().apply {
                        if (zone.isRoundCarpet) {
                            val a = map.mmToCanvas(zone.minXmm, zone.minYmm, w, h)
                            val b = map.mmToCanvas(zone.maxXmm, zone.maxYmm, w, h)
                            addOval(Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
                        } else {
                            val c0 = map.mmToCanvas(zone.x0, zone.y0, w, h); moveTo(c0.x, c0.y)
                            val c1 = map.mmToCanvas(zone.x1, zone.y1, w, h); lineTo(c1.x, c1.y)
                            val c2 = map.mmToCanvas(zone.x2, zone.y2, w, h); lineTo(c2.x, c2.y)
                            val c3 = map.mmToCanvas(zone.x3, zone.y3, w, h); lineTo(c3.x, c3.y)
                            close()
                        }
                    }
                    val c = zoneColor(zone.kind)
                    val selected = selection == Selection.Zone(i)
                    drawPath(path, c.copy(alpha = if (dimmed) 0.12f else 0.25f))
                    drawPath(path, c.copy(alpha = if (dimmed) 0.4f else 0.9f), style = Stroke(if (selected) stroke * 2.5f else stroke))
                }
                walls.forEachIndexed { i, wall ->
                    val selected = selection == Selection.Wall(i)
                    drawLine(
                        color = WALL_COLOR.copy(alpha = if (dimmed) 0.4f else 1f),
                        start = map.mmToCanvas(wall.x0, wall.y0, w, h),
                        end = map.mmToCanvas(wall.x1, wall.y1, w, h),
                        strokeWidth = if (selected) stroke * 3f else stroke * 1.5f,
                        cap = StrokeCap.Round,
                    )
                }

                val s = dragStart
                val e = dragEnd
                if (s != null && e != null) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(12f / scale, 6f / scale))
                    drawLine(
                        color = SPLIT_COLOR,
                        start = s, end = e, strokeWidth = stroke * 1.5f, cap = StrokeCap.Round, pathEffect = dash,
                    )
                }
            }

            val sel = selection
            if (mode == EditMode.RESTRICTIONS && sel != null && canModifySelection) {
                val handles = dragHandles(
                    sel, zones, walls, map, w, h, scale, offset, ROTATE_HANDLE_DISTANCE.toPx(),
                )
                val ring = when (sel) {
                    is Selection.Wall -> WALL_COLOR
                    is Selection.Zone -> zones.getOrNull(sel.index)?.let { zoneColor(it.kind) } ?: WALL_COLOR
                }
                handles.forEach { (target, at) ->
                    val r = HANDLE_DRAW_RADIUS.toPx()
                    if (target is DragTarget.ZoneRotate) {
                        drawCircle(ring, radius = r, center = at)
                        drawCircle(handleColor, radius = r * 0.45f, center = at)
                    } else {
                        drawCircle(handleColor, radius = r, center = at)
                        drawCircle(ring, radius = r, center = at, style = Stroke(2.dp.toPx()))
                    }
                }
            }

            if (mode == EditMode.ROOMS) {
                val focus = when (roomAction) {
                    is RoomAction.Split -> roomAction.segment
                    is RoomAction.Merge -> roomAction.segment
                    RoomAction.None -> selectedRoom
                }
                map.rooms.forEach { room ->
                    val nx = room.labelNormX ?: return@forEach
                    val ny = room.labelNormY ?: return@forEach
                    val center = Offset(nx * w * scale + offset.x, ny * h * scale + offset.y)
                    val highlighted = room.id == focus
                    val layout = textMeasurer.measure(room.name, if (highlighted) selectedLabelStyle else labelStyle)
                    val pad = 6.dp.toPx()
                    val boxW = layout.size.width + pad * 2
                    val boxH = layout.size.height + pad
                    val topLeft = Offset(center.x - boxW / 2, center.y - boxH / 2)
                    drawRoundRect(
                        color = if (highlighted) selectedBg else labelBg,
                        topLeft = topLeft,
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(boxH / 2),
                    )
                    drawText(layout, topLeft = Offset(topLeft.x + pad, topLeft.y + pad / 2))
                }
            }
        }
    }
}

/**
 * Zoom and pan of the editor map, held by the screen so it can place new shapes in the middle of
 * what is visible.
 */
@Stable
private class MapViewport {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var canvasSize by mutableStateOf(IntSize.Zero)
    var framed by mutableStateOf(false)

    /** Robot mm under the centre of the visible map, kept away from the map's edges. */
    fun centerMm(map: ParsedMap): Pair<Int, Int>? {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return null
        val content = (Offset(w / 2f, h / 2f) - offset) / scale
        val (x, y) = map.canvasToMm(content, w, h)
        return x.coerceIn(NEW_SHAPE_MARGIN_MM, MAX_MAP_COORD_MM - NEW_SHAPE_MARGIN_MM) to
            y.coerceIn(NEW_SHAPE_MARGIN_MM, MAX_MAP_COORD_MM - NEW_SHAPE_MARGIN_MM)
    }

    /** Side of a new shape: about a fifth of the visible map width, 0.6-3 m. */
    fun newShapeSizeMm(map: ParsedMap): Int {
        val visibleWidthMm = map.width * MM_PER_CELL / scale
        return (visibleWidthMm * NEW_SHAPE_FRACTION).toInt().coerceIn(600, 3_000)
    }
}

private const val NEW_SHAPE_MARGIN_MM = 1_000
private const val NEW_SHAPE_FRACTION = 0.18f
private const val NOTICE_VISIBLE_MS = 4_000L
private val DOCK_MIN_HEIGHT = 136.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSwitch(mode: EditMode, enabled: Boolean, roomsEnabled: Boolean, onMode: (EditMode) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.width(280.dp).padding(4.dp)) {
            SegmentedButton(
                selected = mode == EditMode.RESTRICTIONS,
                onClick = { onMode(EditMode.RESTRICTIONS) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                enabled = enabled,
            ) { Text("Zones") }
            SegmentedButton(
                selected = mode == EditMode.ROOMS,
                onClick = { onMode(EditMode.ROOMS) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                // Room edits reshape the map under unsaved zone edits, so save or discard first.
                enabled = roomsEnabled,
            ) { Text("Rooms") }
        }
    }
}

/** The docked panel under the map that holds the current mode's controls. */
@Composable
private fun EditorDock(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // A fixed minimum height keeps the map from jumping when the dock switches between the
        // tools and a selected shape's bar.
        Box(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                .heightIn(min = DOCK_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}

private data class ToolSpec(val tool: Tool, val label: String, val icon: ImageVector, val color: Color)

private val TOOL_SPECS = listOf(
    ToolSpec(Tool.NO_GO, "No-go", Icons.Default.Block, NO_GO_COLOR),
    ToolSpec(Tool.WALL, "Wall", Icons.Default.HorizontalRule, WALL_COLOR),
    ToolSpec(Tool.CARPET, "Carpet", Icons.Default.Texture, CARPET_COLOR),
    ToolSpec(Tool.THRESHOLD, "Threshold", Icons.Default.MeetingRoom, THRESHOLD_COLOR),
)

/**
 * Add buttons, one per shape kind. Each tile's colour is the colour that kind has on the map, so
 * the dock doubles as the map's key.
 */
@Composable
private fun ToolDock(toolEnabled: (Tool) -> Boolean, onAdd: (Tool) -> Unit, vertices: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add to map", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "Zone points $vertices/$MAX_RESTRICTION_VERTICES",
                style = MaterialTheme.typography.labelMedium,
                color = if (vertices >= MAX_RESTRICTION_VERTICES) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TOOL_SPECS.forEach { spec ->
                ToolTile(spec, enabled = toolEnabled(spec.tool), onClick = { onAdd(spec.tool) })
            }
        }
    }
}

@Composable
private fun ToolTile(spec: ToolSpec, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Add ${spec.label.lowercase()}", onClick = onClick)
            .widthIn(min = 72.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(spec.color.copy(alpha = 0.18f * alpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(spec.icon, contentDescription = null, tint = spec.color.copy(alpha = alpha), modifier = Modifier.size(26.dp))
        }
        Text(
            spec.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
    }
}

private fun kindTitle(kind: ZoneKind?, round: Boolean = false) = when (kind) {
    null -> "Wall"
    ZoneKind.NO_GO -> "No-go zone"
    ZoneKind.NO_MOP -> "No-mop zone"
    ZoneKind.CARPET -> if (round) "Round carpet" else "Carpet"
    ZoneKind.THRESHOLD -> "Threshold"
}

private fun kindHelp(kind: ZoneKind?, round: Boolean = false) = when {
    round -> "Drag to move. Resize round carpets in the Roborock app."
    else -> kindHelpFor(kind)
}

private fun kindHelpFor(kind: ZoneKind?) = when (kind) {
    null, ZoneKind.THRESHOLD -> "Drag to move, drag the ends to reshape."
    ZoneKind.NO_GO, ZoneKind.CARPET -> "Drag to move. Corners resize, the knob rotates."
    ZoneKind.NO_MOP -> "Set in the Roborock app. LocalRock can't change it yet."
}

/** Replaces the tool dock while a shape is selected: what it is, and what can be done to it. */
@Composable
private fun ShapeBar(kind: ZoneKind?, round: Boolean, canEdit: Boolean, onDelete: () -> Unit, onDone: () -> Unit) {
    val color = kind?.let { zoneColor(it) } ?: WALL_COLOR
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) {
            Text(kindTitle(kind, round), style = MaterialTheme.typography.titleSmall)
            Text(
                if (canEdit || kind == ZoneKind.NO_MOP) kindHelp(kind, round) else "Can't be changed right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canEdit) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${kindTitle(kind).lowercase()}", tint = MaterialTheme.colorScheme.error)
            }
        }
        FilledTonalButton(onClick = onDone) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Done")
        }
    }
}

@Composable
private fun RoomDock(action: RoomAction, roomName: (Int) -> String, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            val (title, help) = when (action) {
                RoomAction.None -> "Rooms" to "Tap a room to rename it, change its floor type, split or merge it."
                is RoomAction.Split -> "Split ${roomName(action.segment)}" to "Draw a line across the room, wall to wall."
                is RoomAction.Merge -> "Merge ${roomName(action.segment)}" to "Tap the neighbouring room to merge with."
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != RoomAction.None) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun RoomSheet(
    map: ParsedMap,
    segment: Int,
    working: Boolean,
    enabled: Boolean,
    onRename: (String) -> Unit,
    onFloor: (FloorChoice) -> Unit,
    onSplit: () -> Unit,
    onMerge: () -> Unit,
) {
    val room = map.rooms.find { it.id == segment }
    val currentName = room?.name ?: "Room $segment"
    var nameInput by remember(segment, currentName) { mutableStateOf(currentName) }
    val nameError = runCatching { validateRoomName(nameInput) }.exceptionOrNull()?.message
    val canAct = enabled && !working

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(currentName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        Text("Name", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.weight(1f),
                enabled = canAct,
            )
            Button(
                onClick = { onRename(nameInput) },
                enabled = canAct && nameError == null && nameInput.trim() != currentName,
            ) { Text("Rename") }
        }

        HorizontalDivider()

        Text("Floor type", style = MaterialTheme.typography.titleSmall)
        val materialId = map.segmentMaterials[segment]
        val direction = map.segmentMaterialDirections[segment]
        val current = FLOOR_CHOICES.firstOrNull { choice ->
            materialId == choice.material.id &&
                (choice.direction == null || direction == choice.direction)
        }
        Text(
            when {
                materialId == null -> "This robot's map doesn't report the current floor type."
                current != null -> "Current: ${current.label}"
                FloorMaterial.fromId(materialId) == FloorMaterial.WOOD -> "Current: Wood"
                else -> "Current: a type LocalRock doesn't know (id $materialId)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FLOOR_CHOICES.take(2).forEach { choice ->
                FilterChip(
                    selected = choice == current,
                    onClick = { if (choice != current) onFloor(choice) },
                    label = { Text(choice.label) },
                    enabled = canAct,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FLOOR_CHOICES.drop(2).forEach { choice ->
                FilterChip(
                    selected = choice == current,
                    onClick = { if (choice != current) onFloor(choice) },
                    label = { Text(choice.label) },
                    enabled = canAct,
                )
            }
        }

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSplit, enabled = canAct, modifier = Modifier.weight(1f)) { Text("Split room") }
            OutlinedButton(onClick = onMerge, enabled = canAct && map.rooms.size > 1, modifier = Modifier.weight(1f)) {
                Text("Merge with...")
            }
        }
    }
}

@Composable
private fun WarningCard(text: String, isError: Boolean = true) {
    Card(
        colors = if (isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

/** Result of the last save or room edit, floating over the bottom of the map. */
@Composable
private fun NoticePill(notice: Notice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (notice.isError) scheme.errorContainer else scheme.inverseSurface,
        contentColor = if (notice.isError) scheme.onErrorContainer else scheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier.widthIn(max = 520.dp),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(notice.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
        }
    }
}

@Composable
private fun MapNotLoaded() {
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
}

private enum class Gesture { DRAW, PAN, EDIT, ZOOM }

/** What a drag on the selected shape changes. */
private sealed interface DragTarget {
    data object Body : DragTarget
    data class ZoneCorner(val index: Int) : DragTarget
    data object ZoneRotate : DragTarget
    /** End 0 or 1 of a wall, or of a threshold's centre line. */
    data class LineEnd(val end: Int) : DragTarget
}

internal const val MAP_LIST_RETRY_MS = 4_000L

private val HANDLE_DRAW_RADIUS = 9.dp
private val HANDLE_TOUCH_RADIUS = 28.dp
private val ROTATE_HANDLE_DISTANCE = 36.dp

/**
 * Round carpets only move: the map stores them as a bounding square plus a flag, and what the robot
 * makes of a stretched or rotated one has not been captured.
 */
private fun canRotateOrResize(zone: MapZone) =
    zone.kind == ZoneKind.NO_GO || (zone.kind == ZoneKind.CARPET && !zone.isRoundCarpet)

/** Screen positions of the selected shape's handles. */
private fun dragHandles(
    sel: Selection,
    zones: List<MapZone>,
    walls: List<VirtualWall>,
    map: ParsedMap,
    w: Float,
    h: Float,
    scale: Float,
    offset: Offset,
    rotateDistancePx: Float,
): List<Pair<DragTarget, Offset>> {
    fun screen(x: Int, y: Int) = map.mmToCanvas(x, y, w, h) * scale + offset
    return when (sel) {
        is Selection.Wall -> walls.getOrNull(sel.index)?.let { wl ->
            listOf(DragTarget.LineEnd(0) to screen(wl.x0, wl.y0), DragTarget.LineEnd(1) to screen(wl.x1, wl.y1))
        } ?: emptyList()
        is Selection.Zone -> {
            val z = zones.getOrNull(sel.index) ?: return emptyList()
            when {
                z.kind == ZoneKind.THRESHOLD -> {
                    val (a, b) = z.thresholdCenterLine()
                    listOf(DragTarget.LineEnd(0) to screen(a.first, a.second), DragTarget.LineEnd(1) to screen(b.first, b.second))
                }
                canRotateOrResize(z) -> {
                    val corners = z.corners().map { (x, y) -> screen(x, y) }
                    val (cxMm, cyMm) = z.center()
                    val c = screen(cxMm.toInt(), cyMm.toInt())
                    val mid = (corners[0] + corners[1]) / 2f
                    val dir = (mid - c).let { d -> if (d.getDistance() < 1f) Offset(0f, -1f) else d / d.getDistance() }
                    corners.mapIndexed { i, at -> DragTarget.ZoneCorner(i) as DragTarget to at } +
                        (DragTarget.ZoneRotate to mid + dir * rotateDistancePx)
                }
                else -> emptyList()
            }
        }
    }
}

/** Handle under [touch], else the selected shape's body, else null (the touch draws or pans). */
private fun hitDragTarget(
    sel: Selection,
    zones: List<MapZone>,
    walls: List<VirtualWall>,
    map: ParsedMap,
    w: Float,
    h: Float,
    scale: Float,
    offset: Offset,
    touch: Offset,
    handleRadiusPx: Float,
    rotateDistancePx: Float,
): DragTarget? {
    dragHandles(sel, zones, walls, map, w, h, scale, offset, rotateDistancePx)
        .minByOrNull { (_, at) -> (at - touch).getDistance() }
        ?.takeIf { (_, at) -> (at - touch).getDistance() <= handleRadiusPx }
        ?.let { return it.first }
    val content = (touch - offset) / scale
    val (xMm, yMm) = map.canvasToMm(content, w, h)
    val slopMm = handleRadiusPx / scale * (map.width / w) * MM_PER_CELL
    val onBody = when (sel) {
        is Selection.Wall -> walls.getOrNull(sel.index)?.let {
            distanceToSegmentMm(xMm, yMm, it.x0, it.y0, it.x1, it.y1) <= slopMm
        } ?: false
        is Selection.Zone -> zones.getOrNull(sel.index)?.let { z ->
            if (z.kind == ZoneKind.THRESHOLD) {
                val (a, b) = z.thresholdCenterLine()
                distanceToSegmentMm(xMm, yMm, a.first, a.second, b.first, b.second) <= slopMm
            } else z.contains(xMm, yMm)
        } ?: false
    }
    return if (onBody) DragTarget.Body else null
}

/**
 * Apply a drag from [startMm] to [nowMm] to the shape as it was when the drag began, so rounding
 * never accumulates. Out-of-map results are dropped by the callers.
 */
private fun applyEdit(
    target: DragTarget,
    sel: Selection,
    startZone: MapZone?,
    startWall: VirtualWall?,
    startMm: Pair<Int, Int>,
    nowMm: Pair<Int, Int>,
    onZone: (Int, MapZone) -> Unit,
    onWall: (Int, VirtualWall) -> Unit,
) {
    val dx = nowMm.first - startMm.first
    val dy = nowMm.second - startMm.second
    when (sel) {
        is Selection.Wall -> {
            val wl = startWall ?: return
            val moved = when (target) {
                DragTarget.Body -> wl.translated(dx, dy)
                is DragTarget.LineEnd -> {
                    val (ex, ey) = if (target.end == 0) wl.x0 to wl.y0 else wl.x1 to wl.y1
                    val next = wl.withEnd(target.end, ex + dx, ey + dy)
                    if (hypot((next.x1 - next.x0).toFloat(), (next.y1 - next.y0).toFloat()) < MIN_SHAPE_MM) return
                    next
                }
                else -> return
            }
            onWall(sel.index, moved)
        }
        is Selection.Zone -> {
            val z = startZone ?: return
            val moved = when (target) {
                DragTarget.Body -> z.translated(dx, dy)
                is DragTarget.ZoneCorner -> {
                    val (cx, cy) = z.corners()[target.index]
                    z.resizedFromCorner(target.index, cx + dx, cy + dy, MIN_SHAPE_MM)
                }
                DragTarget.ZoneRotate -> {
                    val (cx, cy) = z.center()
                    val before = kotlin.math.atan2(startMm.second - cy, startMm.first - cx)
                    val after = kotlin.math.atan2(nowMm.second - cy, nowMm.first - cx)
                    z.rotated(after - before)
                }
                is DragTarget.LineEnd -> {
                    if (z.kind != ZoneKind.THRESHOLD) return
                    val (a, b) = z.thresholdCenterLine()
                    val na = if (target.end == 0) (a.first + dx) to (a.second + dy) else a
                    val nb = if (target.end == 1) (b.first + dx) to (b.second + dy) else b
                    if (hypot((nb.first - na.first).toFloat(), (nb.second - na.second).toFloat()) < MIN_SHAPE_MM) return
                    thresholdStrip(na, nb)
                }
            }
            onZone(sel.index, moved)
        }
    }
}

