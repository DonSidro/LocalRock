package com.kodraliu.localrock.ui.vacuum

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.RoomCleanMode
import com.kodraliu.localrock.shared.vacuum.SWITCH_MAP_MODE_SMART
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.isSmartModeSet
import com.kodraliu.localrock.shared.vacuum.mapNameBlocker
import com.kodraliu.localrock.shared.vacuum.roomCleanModesFor
import com.kodraliu.localrock.shared.vacuum.washFrequencyLabel
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.roomAtNorm
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.LocalWindowLayout
import kotlinx.coroutines.delay


private val FAN_OPTIONS = listOf(
    VacuumFanPower.QUIET to "Quiet",
    VacuumFanPower.BALANCED to "Standard",
    VacuumFanPower.TURBO to "Strong",
    VacuumFanPower.MAX to "MAX",
)

/** A room can also go without suction, the official app's mop-only setting. */
private val ROOM_FAN_OPTIONS = listOf(VacuumFanPower.OFF to "Off") + FAN_OPTIONS

private val WATER_OPTIONS = listOf(
    WaterBoxMode.OFF to "Off",
    WaterBoxMode.LOW to "Low",
    WaterBoxMode.MEDIUM to "Med",
    WaterBoxMode.HIGH to "High",
)

private val ROUTE_OPTIONS = listOf(
    MopRoute.STANDARD to "Standard",
    MopRoute.DEEP to "Deep",
    MopRoute.DEEP_PLUS to "Deep+",
    MopRoute.FAST to "Fast",
)

private val ACTIVE_CLEANING_STATES = setOf(
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VacuumDetailScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onZoneClean: () -> Unit,
    onMapEdit: () -> Unit,
    onSchedule: () -> Unit,
    onCamera: () -> Unit,
    onRemote: () -> Unit,
    onPinGo: () -> Unit,
    onHistory: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val mapRotation by viewModel.mapRotation.collectAsState()
    val map3DYaw by viewModel.map3DYaw.collectAsState()
    val map3DTilt by viewModel.map3DTilt.collectAsState()
    val map3D by viewModel.map3D.collectAsState()
    val supportsSmartPlan by viewModel.supportsSmartPlan.collectAsState()
    val supportsWaterSlide by viewModel.supportsWaterSlide.collectAsState()
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val floorMaps by viewModel.repository.floorMaps.collectAsState()
    val currentFloorFlag by viewModel.repository.currentFloorFlag.collectAsState()
    val maxFloorMaps by viewModel.repository.maxFloorMaps.collectAsState()
    val unsavedMap by viewModel.repository.unsavedMapPresent.collectAsState()
    val mopMode by viewModel.repository.mopMode.collectAsState()
    val roomCleanModes by viewModel.repository.roomCleanModes.collectAsState()
    val dockSettings by viewModel.repository.dockSettings.collectAsState()
    var selectedRoomIds by remember { mutableStateOf(emptyList<Int>()) }

    var sessionRoomIds by remember { mutableStateOf(emptyList<Int>()) }
    var panel by remember { mutableStateOf<DetailPanel?>(null) }
    var openObstacle by remember { mutableStateOf<MapObstacle?>(null) }
    val twoPane = LocalWindowLayout.current.twoPane
    BackHandler(enabled = twoPane && sidePaneShowsPanel(panel)) { panel = null }
    val routines by viewModel.routines.collectAsState()
    var recoverLoading by remember { mutableStateOf(false) }
    var recoverTick by remember { mutableIntStateOf(0) }
    var renamingMap by remember { mutableStateOf<FloorMap?>(null) }
    var deletingMap by remember { mutableStateOf<FloorMap?>(null) }
    var choosingMapToKeep by remember { mutableStateOf(false) }
    var lastCleanArea by remember { mutableStateOf<Long?>(null) }
    var cleaningCount by remember { mutableIntStateOf(1) }
    // The map's pan/zoom lives here so it survives the compact ↔ two-pane switch.
    val mapViewport = remember { LiveMapViewport() }


    val sessionRoomNames = remember(sessionRoomIds, status.state, parsedMap) {
        if (status.state == VacuumStateCodes.SEGMENT_CLEANING && sessionRoomIds.isNotEmpty()) {
            val byId = parsedMap?.rooms?.associateBy { it.id } ?: emptyMap()
            sessionRoomIds.map { id -> byId[id]?.name ?: "Room $id" }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(status.cleanArea) {
        if ((status.cleanArea ?: 0L) > 0L) lastCleanArea = status.cleanArea
    }
    // The first request can go out before the robot link is up, so retry until the list arrives.
    // map_status and lab_status change when a map is saved, deleted or switched (also from the
    // official app), so the list is read again then and the floor picker stays current.
    LaunchedEffect(status.mapStatus, status.labStatus) {
        do {
            runCatching { viewModel.repository.fetchFloorMaps() }
            if (!viewModel.repository.floorMapsLoaded.value) delay(MAP_LIST_RETRY_MS)
        } while (!viewModel.repository.floorMapsLoaded.value)
    }
    LaunchedEffect(panel == DetailPanel.CleaningMode) {
        if (panel == DetailPanel.CleaningMode) runCatching { viewModel.repository.fetchMopMode() }
    }
    val perRoomMode = isCustomizeModeSet(status.fanPower, status.waterBoxCustomMode, mopMode)
    // Null while the robot's room settings are being read, false when that failed. Saving is only
    // offered once they are known, or the defaults shown meanwhile would overwrite them.
    var roomModesLoaded by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(panel == DetailPanel.CleaningMode, perRoomMode) {
        roomModesLoaded = null
        if (panel == DetailPanel.CleaningMode && perRoomMode) {
            roomModesLoaded = runCatching { viewModel.repository.fetchRoomCleanModes() }.isSuccess
        }
    }
    LaunchedEffect(panel == DetailPanel.Dock) {
        if (panel == DetailPanel.Dock) runCatching { viewModel.repository.fetchDockSettings() }
    }
    LaunchedEffect(panel == DetailPanel.Routines) {
        if (panel == DetailPanel.Routines) viewModel.loadRoutines()
    }

    LaunchedEffect(panel == DetailPanel.Maps, recoverTick) {
        if (panel == DetailPanel.Maps) {
            recoverLoading = true
            runCatching { viewModel.repository.fetchFloorMaps() }
            recoverLoading = false
        }
    }

    val onMoreAction: (MoreAction) -> Unit = { action ->
        when (action) {
            MoreAction.ROOMS -> panel = DetailPanel.Rooms
            MoreAction.DOCK -> panel = DetailPanel.Dock
            MoreAction.MAPS -> panel = DetailPanel.Maps
            MoreAction.ROUTINES -> panel = DetailPanel.Routines
            MoreAction.ZONES -> { panel = null; onZoneClean() }
            MoreAction.PIN_GO -> { panel = null; onPinGo() }
            MoreAction.REMOTE -> { panel = null; onRemote() }
            MoreAction.SCHEDULE -> { panel = null; onSchedule() }
            MoreAction.CAMERA -> { panel = null; onCamera() }
            MoreAction.HISTORY -> { panel = null; onHistory() }
            MoreAction.EDIT_MAP -> { panel = null; onMapEdit() }
        }
    }

    val onLoadMap: () -> Unit = { viewModel.refreshMap() }
    val onRecover: () -> Unit = { panel = DetailPanel.Maps }
    val onRoomTap: (Int) -> Unit = { roomId ->
        selectedRoomIds = if (roomId in selectedRoomIds)
            selectedRoomIds.filter { it != roomId }
        else selectedRoomIds + roomId
    }
    val onFloorSwitch: (Int) -> Unit = { flag -> viewModel.run { it.switchFloor(flag) } }

    val PanelBody: @Composable (DetailPanel) -> Unit = { currentPanel ->
        when (currentPanel) {
            DetailPanel.More -> MoreActionsSheetContent(onAction = onMoreAction)

            DetailPanel.Routines -> RoutinesSheetContent(
                state = routines,
                busy = busy,
                onRun = { scene ->
                    panel = null
                    viewModel.runRoutine(scene)
                },
                onRetry = { viewModel.loadRoutines() },
            )

            DetailPanel.Rooms -> RoomsSheetContent(
                rooms = parsedMap?.rooms ?: emptyList(),
                selectedRoomIds = selectedRoomIds,
                busy = busy,
                onRoomToggle = { roomId ->
                    selectedRoomIds = if (roomId in selectedRoomIds)
                        selectedRoomIds.filter { it != roomId }
                    else selectedRoomIds + roomId
                },
                onCleanRooms = {
                    val toClean = selectedRoomIds
                    sessionRoomIds = toClean
                    selectedRoomIds = emptyList()
                    panel = null
                    viewModel.run { it.cleanRooms(toClean, cleaningCount) }
                },
                onLoadMap = {
                    panel = null
                    viewModel.refreshMap()
                },
                onDefaultOrder = {
                    panel = DetailPanel.Order
                },
            )

            DetailPanel.Order -> CleanOrderSheetContent(
                rooms = parsedMap?.rooms ?: emptyList(),
                savedOrder = remember(currentFloorFlag) { viewModel.defaultCleanOrder() },
                busy = busy,
                onSave = { order ->
                    panel = null
                    viewModel.saveDefaultCleanOrder(order)
                },
                onRemove = {
                    panel = null
                    viewModel.removeDefaultCleanOrder()
                },
            )

            DetailPanel.Dock -> DockSheetContent(
                status = status,
                busy = busy,
                dockSettings = dockSettings,
                dockCapabilities = viewModel.dockCapabilities,
                onEmptyDustbin = { panel = null; viewModel.run { it.collectDust() } },
                onWashMop = { panel = null; viewModel.run { it.washMop() } },
                // Dry keeps the sheet open so the drying status row appears in place.
                onDryMop = { viewModel.run { it.startDryer() } },
                onStopDry = { viewModel.run { it.stopDryer() } },
                onWashModeChange = { mode -> viewModel.run { repo -> repo.setWashMode(mode) } },
                onWashFrequencyChange = { mode, minutes -> viewModel.run { repo -> repo.setWashFrequency(mode, minutes) } },
                onAutoEmptyModeChange = { mode -> viewModel.run { repo -> repo.setAutoEmptyMode(mode) } },
            )

            DetailPanel.CleaningMode -> CleaningModeSheetContent(
                currentFanPower = status.fanPower,
                currentWaterMode = status.waterBoxCustomMode,
                currentRoute = mopMode,
                cleaningCount = cleaningCount,
                busy = busy,
                supportsSmartPlan = supportsSmartPlan,
                rooms = parsedMap?.rooms ?: emptyList(),
                roomCleanModes = roomCleanModes,
                roomModesLoaded = roomModesLoaded,
                onPlanSelected = { plan ->
                    viewModel.run {
                        when (plan) {
                            CleanPlan.SMART -> it.setCleanMotorMode(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART)
                            CleanPlan.MANUAL -> {
                                // Water slide robots get a slide code, as python-roborock does.
                                val water = if (supportsWaterSlide) WaterBoxMode.SLIDE_MEDIUM else WaterBoxMode.LOW
                                it.setCleanMotorMode(VacuumFanPower.BALANCED, water, MopRoute.STANDARD)
                            }
                            // The three codes the official app sent for its Customize tab.
                            CleanPlan.PER_ROOM -> it.setCleanMotorMode(VacuumFanPower.CUSTOM, WaterBoxMode.CUSTOM, MopRoute.CUSTOM)
                        }
                    }
                },
                onSaveRoomModes = { modes -> viewModel.run("Room settings saved") { it.saveRoomCleanModes(modes) } },
                onFanPowerSelected = { level -> viewModel.run { it.setFanPower(level) } },
                onWaterModeSelected = { level -> viewModel.run { it.setWaterBoxMode(level) } },
                onRouteSelected = { mode -> viewModel.run { it.setMopMode(mode) } },
                onCountChange = { cleaningCount = it },
            )

            DetailPanel.Maps -> MapsSheetContent(
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                loading = recoverLoading,
                busy = busy,
                blockedReason = viewModel.repository.mapManageBlockedReason(status.state),
                canAddMap = floorMaps.size < (maxFloorMaps ?: 1),
                unsavedMap = unsavedMap,
                mapSaving = status.mapSavingEnabled,
                multiLevel = status.multiLevelEnabled,
                smartSwitching = status.switchMapMode?.let { it == SWITCH_MAP_MODE_SMART },
                onMultiLevelChange = { on ->
                    when {
                        on -> viewModel.run("Multi-level maps on") { it.setMultiLevel(true, null) }
                        floorMaps.size > 1 -> choosingMapToKeep = true
                        else -> viewModel.run("Multi-level maps off") { it.setMultiLevel(false, null) }
                    }
                },
                onSmartSwitchingChange = { smart -> viewModel.run { it.setSmartMapSwitching(smart) } },
                onEnableMapSaving = { viewModel.run("Map saving on") { it.enableMapSaving() } },
                onLoad = { flag ->
                    panel = null
                    viewModel.run { it.switchFloor(flag) }
                },
                onRename = { renamingMap = it },
                onDelete = { deletingMap = it },
                onStartMapping = {
                    panel = null
                    viewModel.run("Mapping started") { it.startMapping() }
                },
                onSaveNewMap = { viewModel.run("New map saved") { it.saveNewMap() } },
                onRecheck = { recoverTick++ },
            )
        }
    }

    // Status + controls live in one band so the phone and two-pane layouts cannot silently diverge.
    val ControlBand: @Composable () -> Unit = {
        StatusBar(
            status = status,
            lastCleanArea = lastCleanArea,
            onHistory = onHistory,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        )

        ControlBar(
            status = status,
            busy = busy,
            selectedRoomCount = selectedRoomIds.size,
            onClean = {
                sessionRoomIds = emptyList()
                val order = viewModel.defaultCleanOrder()
                viewModel.run("Cleaning started") { it.clean(order) }
            },
            onCleanRooms = {
                val toClean = selectedRoomIds
                sessionRoomIds = toClean
                selectedRoomIds = emptyList()
                viewModel.run { it.cleanRooms(toClean, cleaningCount) }
            },
            onClearSelection = { selectedRoomIds = emptyList() },
            onPause = { viewModel.run { it.pause() } },
            onResume = { viewModel.run("Cleaning resumed") { it.resumeCleaning() } },
            onStop = {
                sessionRoomIds = emptyList()
                viewModel.run { it.stopCleaning() }
            },
            onDock = {
                sessionRoomIds = emptyList()
                viewModel.run("Returning to dock") { it.dock() }
            },
            onCleaningMode = { panel = DetailPanel.CleaningMode },
            onMore = { panel = DetailPanel.More },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(viewModel.deviceName, style = MaterialTheme.typography.titleLarge)
                        viewModel.modelDisplayName?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        val MapHero: @Composable (Modifier) -> Unit = { modifier ->
            MapHeroSection(
                deviceId = viewModel.repository.duid,
                parsedMap = parsedMap,
                busy = busy,
                selectedRoomIds = selectedRoomIds,
                sessionRoomNames = sessionRoomNames,
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                rotationDegrees = mapRotation,
                yaw3D = map3DYaw,
                onYawBy = viewModel::turnMap3DBy,
                tilt3D = map3DTilt,
                onTiltBy = viewModel::tiltMap3DBy,
                is3D = map3D,
                onLoadMap = onLoadMap,
                onRecover = onRecover,
                onRoomTap = onRoomTap,
                onObstacleTap = { openObstacle = it },
                onFloorSwitch = onFloorSwitch,
                onEditMap = onMapEdit,
                // Each view keeps its own heading: this button turns whichever one is showing.
                onRotate = { if (map3D) viewModel.turnMap3DBy(90f) else viewModel.rotateMap90() },
                onToggle3D = { viewModel.setMap3D(!map3D) },
                mapViewport = mapViewport,
                modifier = modifier,
            )
        }

        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MapHero(Modifier.weight(1f).fillMaxSize())

                Column(
                    modifier = Modifier
                        .widthIn(min = 320.dp, max = 480.dp)
                        .fillMaxWidth(0.42f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .drawBehind {
                            drawLine(dividerColor, Offset.Zero, Offset(0f, size.height), 1.dp.toPx())
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .consumeWindowInsets(WindowInsets.navigationBars),
                    ) {
                        val headed = panel.takeIf { sidePaneShowsPanel(it) }
                        if (headed != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { panel = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                                Text(headed.title(), style = MaterialTheme.typography.titleMedium)
                            }
                            PanelBody(headed)
                        } else {
                            MoreActionsSheetContent(onAction = onMoreAction)
                        }
                    }

                    ControlBand()
                }
            }
        } else {
            // The map is the screen. Status and controls sit in a fixed band beneath it so the hero
            // never scrolls out of view and the primary action never moves under the thumb.
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MapHero(Modifier.weight(1f).fillMaxSize())

                ControlBand()
            }
        }
    }

    openObstacle?.let { obstacle ->
        ModalBottomSheet(onDismissRequest = { openObstacle = null }) {
            ObstacleSheetContent(obstacle, loadPhoto = { viewModel.repository.fetchObstaclePhoto(it) })
        }
    }

    if (!twoPane) panel?.let { current ->
        ModalBottomSheet(onDismissRequest = { panel = null }) { PanelBody(current) }
    }

    renamingMap?.let { floor ->
        RenameMapDialog(
            floor = floor,
            onConfirm = { name ->
                renamingMap = null
                viewModel.run("Map renamed") { it.renameFloorMap(floor.mapFlag, name) }
            },
            onDismiss = { renamingMap = null },
        )
    }

    if (choosingMapToKeep && floorMaps.isNotEmpty()) {
        KeepMapDialog(
            floorMaps = floorMaps,
            currentFloorFlag = currentFloorFlag,
            onConfirm = { keep ->
                choosingMapToKeep = false
                viewModel.run("Multi-level maps off") { it.setMultiLevel(false, keep) }
            },
            onDismiss = { choosingMapToKeep = false },
        )
    }

    deletingMap?.let { floor ->
        DeleteMapDialog(
            floor = floor,
            onConfirm = {
                deletingMap = null
                viewModel.run("Map deleted") { it.deleteFloorMap(floor.mapFlag) }
            },
            onDismiss = { deletingMap = null },
        )
    }
}


@Composable
private fun MapHeroSection(
    deviceId: String,
    parsedMap: ParsedMap?,
    busy: Boolean,
    selectedRoomIds: List<Int>,
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val heroBackground = if (scheme.surface.luminance() < 0.5f) {
        scheme.surfaceVariant
    } else {
        // Near-white, so the tinted rooms advance instead of sinking into a grey slab.
        scheme.surfaceContainerLowest
    }
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
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MapEmptyState(busy = busy, onLoadMap = onLoadMap, onRecover = onRecover, modifier = Modifier.fillMaxSize())
        }

        // Refresh and Edit map are the only controls on top of the map; camera, remote and map
        // recovery live in the More sheet so the hero reads as one surface. Edit sits in the
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
            if (floorMaps.size > 1) {
                FloorPicker(
                    floorMaps = floorMaps,
                    currentFloorFlag = currentFloorFlag,
                    enabled = !busy,
                    onFloorSwitch = onFloorSwitch,
                )
            }
            if (sessionRoomNames.isNotEmpty()) {
                SessionRoomsBadge(roomNames = sessionRoomNames)
            }
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

@Composable
private fun VacuumMapImage(
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
) {
    // Drawn larger than the grid so a zoomed-in view stays crisp; every length below that is measured
    // in the layers' own pixels (wall height, label lift) is in cells times this.
    val scale = remember(map) { supersampleFor(map) }
    val (bitmap, overlayBitmap) = rememberMapLayers(map, scale)
    val walls = rememberWallBitmaps(map, scale, enabled = is3D)
    val selectionBitmap = rememberSelectionBitmap(map, selectedRoomIds, scale)
    val wallHeight = WALL_HEIGHT_CELLS * scale
    val labelRooms = remember(map.rooms) { map.rooms.filter { it.labelNormX != null && it.labelNormY != null } }
    val textMeasurer = rememberTextMeasurer()
    // Labels follow the scheme rather than being a fixed black scrim: over a light map a dark
    // scrim reads as a scattering of heavy blobs and dominates the surface it sits on.
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val selectedBgColor = MaterialTheme.colorScheme.primary
    // In dark mode `primary` is a bright cyan, so white-on-primary would be unreadable.
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onPrimary)
    val currentOnRoomTap by rememberUpdatedState(onRoomTap)
    val currentOnObstacleTap by rememberUpdatedState(onObstacleTap)
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

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                        val fit = currentViewFor(size.width.toFloat(), size.height.toFloat()).k / mapViewport.zoom
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
                            val tapView = currentViewFor(size.width.toFloat(), size.height.toFloat())
                            currentMap.obstacleAt(tap, tapView, currentBitmap.width / currentMap.width.toFloat(), 20.dp.toPx())
                                ?.let { currentOnObstacleTap(it); return@detectTapGestures }
                            // The same transform that drew the map, run backwards, so a tap lands on
                            // the room under the finger at any heading, zoom or tilt.
                            val at = tapView.toBitmap(tap.x, tap.y)
                            currentMap.roomAtNorm(at.x / currentBitmap.width, at.y / currentBitmap.height)
                                ?.let { currentOnRoomTap(it.id) }
                        },
                    )
                },
        ) {
            val view = viewFor(size.width, size.height)
            // Magnified past 2 screen pixels per layer pixel, nearest-neighbour keeps edges crisp
            // where smoothing would blur them; so does a flat quarter-turn, which maps pixels 1:1.
            // Zoomed out, where many pixels share one on screen, smoothing avoids shimmer.
            val flatQuarterTurn = !is3D && yawDegrees % 90f == 0f
            val quality = if ((flatQuarterTurn && view.k >= 0.95f) || view.k >= 2f) FilterQuality.None else FilterQuality.Low
            drawMapLayer(bitmap, view, 0f, quality)
            // A selected room is tinted over the floor, and the path goes over the tint, so the
            // choice reads at a glance without hiding where the robot has been.
            selectionBitmap?.let { drawMapLayer(it, view, 0f, quality) }
            drawMapLayer(overlayBitmap, view, 0f, quality)
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
            drawObstacles(map, view, bitmap.width / map.width.toFloat())

            // Labels are drawn outside the zoom transform so they stay a constant, legible size
            // instead of growing to 8x with the map. Selected rooms carry their cleaning order,
            // so selection reads without relying on the highlight colour alone.
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
                val style = if (selected) selectedLabelStyle else labelStyle
                val measured = textMeasurer.measure(text, style)
                val tw = measured.size.width.toFloat()
                val th = measured.size.height.toFloat()
                val padX = 6.dp.toPx()
                val padY = 3.dp.toPx()
                drawRoundRect(
                    color = if (selected) selectedBgColor else bgColor,
                    topLeft = Offset(cx - tw / 2 - padX, cy - th / 2 - padY),
                    size = Size(tw + padX * 2, th + padY * 2),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = text,
                    style = style,
                    topLeft = Offset(cx - tw / 2, cy - th / 2),
                )
            }
        }

    }
}

@Composable
private fun MapEmptyState(busy: Boolean, onLoadMap: () -> Unit, onRecover: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Map, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("No map loaded", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("Load the latest map from your robot.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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


@Composable
private fun StatusBar(
    status: VacuumStatus,
    lastCleanArea: Long?,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // clean_area is reported in mm²; show m² with one decimal.
    val area = lastCleanArea?.let { mm2 ->
        val tenths = mm2 / 100_000
        "${tenths / 10}.${tenths % 10} m²"
    }
    val time = status.cleanTime?.takeIf { it > 0 }?.let { s ->
        val m = s / 60
        if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    }

    val errorCode = status.errorCode ?: 0
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP
    val active = drying || isBusyState(status.state)
    // The robot's own error is the most important thing it can tell you, so it replaces the state
    // label in place rather than only living in the app-wide banner.
    val headline = when {
        errorCode != 0 -> VacuumErrorCodes.describe(errorCode)
        drying -> dryingLabel(status.remainingDryTimeSec)
        else -> stateLabel(status.state)
    }
    val headlineColor = when {
        errorCode != 0 -> MaterialTheme.colorScheme.error
        drying -> MaterialTheme.colorScheme.tertiary
        active -> stateTint(status.state)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val cleaningNow = status.state in ACTIVE_CLEANING_STATES
    val detail = buildList {
        if (area != null) add(if (cleaningNow) "$area so far" else "$area last clean")
        if (time != null) add(time)
    }.joinToString(" · ").ifEmpty { "No cleaning recorded yet" }

    val charging = status.chargeStatus == 1 || status.state == VacuumStateCodes.CHARGING
    val battery = status.battery
    val batteryIcon = when {
        charging -> Icons.Default.BatteryChargingFull
        battery == null -> Icons.AutoMirrored.Filled.BatteryUnknown
        battery >= 95 -> Icons.Default.BatteryFull
        battery >= 80 -> Icons.Default.Battery6Bar
        battery >= 65 -> Icons.Default.Battery5Bar
        battery >= 50 -> Icons.Default.Battery4Bar
        battery >= 35 -> Icons.Default.Battery3Bar
        battery >= 20 -> Icons.Default.Battery2Bar
        else -> Icons.Default.Battery1Bar
    }

    Surface(
        onClick = onHistory,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        errorCode != 0 -> Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        active -> PulsingDot(headlineColor)
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = headlineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    batteryIcon,
                    contentDescription = if (charging) "Battery, charging" else "Battery",
                    modifier = Modifier.size(20.dp),
                    tint = batteryColor(battery),
                )
                Text(
                    battery?.let { "$it%" } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlBar(
    status: VacuumStatus,
    busy: Boolean,
    selectedRoomCount: Int,
    onClean: () -> Unit,
    onCleanRooms: () -> Unit,
    onClearSelection: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    onCleaningMode: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCleaning = status.state in ACTIVE_CLEANING_STATES
    val isPaused = status.state == VacuumStateCodes.PAUSED
    val mopping = (status.waterBoxCustomMode ?: WaterBoxMode.OFF) != WaterBoxMode.OFF

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onCleaningMode,
                label = {
                    Text(
                        when {
                            isSmartModeSet(status.fanPower, status.waterBoxCustomMode, null) -> CleanPlan.SMART.label
                            isCustomizeModeSet(status.fanPower, status.waterBoxCustomMode, null) -> CleanPlan.PER_ROOM.label
                            mopping && status.fanPower == VacuumFanPower.OFF -> "Mop"
                            mopping -> "Vac & Mop"
                            else -> "Vacuum"
                        }
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            // Tapping rooms on the map used to have no visible consequence. The selection now
            // surfaces here and retargets the primary action.
            if (selectedRoomCount > 0) {
                InputChip(
                    selected = true,
                    onClick = onClearSelection,
                    label = {
                        Text(if (selectedRoomCount == 1) "1 room" else "$selectedRoomCount rooms")
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear room selection",
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // One prominent button per view: its label and target follow the robot's state.
            val primaryLabel = when {
                isCleaning -> "Pause"
                selectedRoomCount > 0 -> "Clean rooms"
                isPaused -> "Resume"
                else -> "Clean"
            }
            Button(
                onClick = {
                    when {
                        isCleaning -> onPause()
                        selectedRoomCount > 0 -> onCleanRooms()
                        isPaused -> onResume()
                        else -> onClean()
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Icon(
                    if (isCleaning) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    primaryLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCleaning || isPaused) {
                FilledTonalIconButton(
                    onClick = onStop,
                    enabled = !busy,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop cleaning", modifier = Modifier.size(24.dp))
                }
            }
            FilledTonalIconButton(
                onClick = onDock,
                enabled = !busy,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Default.Home, contentDescription = "Return to dock", modifier = Modifier.size(24.dp))
            }
            FilledTonalIconButton(onClick = onMore, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "More actions", modifier = Modifier.size(24.dp))
            }
        }
    }
}


private enum class MoreAction { ROOMS, ZONES, PIN_GO, REMOTE, SCHEDULE, CAMERA, DOCK, HISTORY, MAPS, EDIT_MAP, ROUTINES }

@Composable
private fun MoreActionsSheetContent(onAction: (MoreAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Scrolls on short screens where the half-expanded sheet can't show every row.
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "More actions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.GridView, "Rooms", MaterialTheme.colorScheme.primary) { onAction(MoreAction.ROOMS) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.SelectAll, "Zone clean", AppColors.AccentIndigo) { onAction(MoreAction.ZONES) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Place, "Pin & Go", AppColors.AccentPurple) { onAction(MoreAction.PIN_GO) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.Gamepad, "Remote", AppColors.AccentCyan) { onAction(MoreAction.REMOTE) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Schedule, "Schedule", AppColors.Water) { onAction(MoreAction.SCHEDULE) }
            // Camera is hidden for now; MoreAction.CAMERA and the live view screen stay in place.
            MoreActionItem(Modifier.weight(1f), Icons.Default.Edit, "Edit map", AppColors.AccentRed) { onAction(MoreAction.EDIT_MAP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.Default.Dock, "Dock & care", AppColors.Good) { onAction(MoreAction.DOCK) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.History, "History", MaterialTheme.colorScheme.tertiary) { onAction(MoreAction.HISTORY) }
            MoreActionItem(Modifier.weight(1f), Icons.Default.Map, "Maps", AppColors.Warn) { onAction(MoreAction.MAPS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoreActionItem(Modifier.weight(1f), Icons.AutoMirrored.Filled.PlaylistPlay, "Routines", MaterialTheme.colorScheme.secondary) { onAction(MoreAction.ROUTINES) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Routines made in the official app. local_roborock_server stores and runs them; the app only lists and starts them. */
@Composable
private fun RoutinesSheetContent(
    state: RoutinesState,
    busy: Boolean,
    onRun: (Scene) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routines", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Routines saved in the Roborock app, kept by your server. Running one that is already going stops it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            RoutinesState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is RoutinesState.Failed -> {
                Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }
            is RoutinesState.Loaded -> if (state.routines.isEmpty()) {
                Text(
                    "No routines for this robot. Routines are made in the Roborock app; once the server has them they show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.routines.forEach { scene ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                scene.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FilledTonalButton(onClick = { onRun(scene) }, enabled = !busy) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreActionItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleaningModeSheetContent(
    currentFanPower: Int?,
    currentWaterMode: Int?,
    currentRoute: Int?,
    cleaningCount: Int,
    busy: Boolean,
    supportsSmartPlan: Boolean,
    rooms: List<ParsedMapRoom>,
    roomCleanModes: List<RoomCleanMode>,
    roomModesLoaded: Boolean?,
    onPlanSelected: (CleanPlan) -> Unit,
    onSaveRoomModes: (List<RoomCleanMode>) -> Unit,
    onFanPowerSelected: (Int) -> Unit,
    onWaterModeSelected: (Int) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
) {
    val initialTab = when {
        (currentWaterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF -> 2
        currentFanPower == VacuumFanPower.QUIET -> 1
        else -> 0
    }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    // SmartPlan owns suction, water and route; the robot rejects manual changes until it is left.
    val smart = isSmartModeSet(currentFanPower, currentWaterMode, currentRoute)
    // Per-room settings report their own codes for all three as well.
    val perRoom = isCustomizeModeSet(currentFanPower, currentWaterMode, currentRoute)
    val plan = when {
        smart -> CleanPlan.SMART
        perRoom -> CleanPlan.PER_ROOM
        else -> CleanPlan.MANUAL
    }
    val plans = buildList {
        if (supportsSmartPlan || smart) add(CleanPlan.SMART)
        add(CleanPlan.MANUAL)
        if (rooms.isNotEmpty() || perRoom) add(CleanPlan.PER_ROOM)
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        if (plans.size > 1) {
            Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                ModeOptionSelector("Mode", plans.map { it.label }, plans.indexOf(plan), !busy) {
                    if (plans[it] != plan) onPlanSelected(plans[it])
                }
            }
        }
        if (smart) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("SmartPlan picks suction, water and route for each room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountSelector(cleaningCount, onCountChange)
            }
        } else if (perRoom) {
            if (roomModesLoaded == true) {
                RoomCleanModesEditor(rooms, roomCleanModes, busy, onSaveRoomModes)
            } else {
                Text(
                    if (roomModesLoaded == null) "Reading the room settings from the robot."
                    else "Couldn't read the room settings from the robot. Close this sheet and open it again.",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                listOf("Vac & Mop", "Mop", "Vacuum").forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = {
                            selectedTab = i
                            when (i) {
                                0 -> { if ((currentWaterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF) onWaterModeSelected(WaterBoxMode.LOW) }
                                1 -> { onFanPowerSelected(VacuumFanPower.QUIET); if ((currentWaterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF) onWaterModeSelected(WaterBoxMode.LOW) }
                                2 -> { onWaterModeSelected(WaterBoxMode.OFF) }
                            }
                        },
                        text = { Text(title) },
                    )
                }
            }

            Box(Modifier.fillMaxWidth().heightIn(min = 380.dp)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val subtitle = when (selectedTab) {
                        0 -> "Vac & Mop for daily clean"
                        1 -> "Mop only for delicate floors"
                        else -> "Vacuum only mode"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val suction = @Composable {
                        ModeOptionSelector("Suction power", FAN_OPTIONS.map { it.second }, FAN_OPTIONS.indexOfFirst { it.first == currentFanPower }, !busy) { onFanPowerSelected(FAN_OPTIONS[it].first) }
                    }
                    val water = @Composable {
                        ModeOptionSelector("Water flow", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(currentWaterMode) }, !busy) { onWaterModeSelected(WATER_OPTIONS[it].first) }
                    }
                    val route = @Composable {
                        ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == currentRoute }, !busy) { onRouteSelected(ROUTE_OPTIONS[it].first) }
                    }

                    when (selectedTab) {
                        0 -> { suction(); water(); CountSelector(cleaningCount, onCountChange); route() }
                        1 -> { water(); CountSelector(cleaningCount, onCountChange); route() }
                        2 -> { suction(); CountSelector(cleaningCount, onCountChange) }
                    }
                }
            }
        }
    }
}

private enum class CleanPlan(val label: String) {
    SMART("SmartPlan"),
    MANUAL("Manual"),
    PER_ROOM("Per room"),
}

private fun roomModeSummary(mode: RoomCleanMode): String {
    val fan = ROOM_FAN_OPTIONS.firstOrNull { it.first == mode.fanPower }?.second ?: "Other"
    val water = WATER_OPTIONS.firstOrNull { it.first == WaterBoxMode.normalize(mode.waterBoxMode) }?.second ?: "Other"
    return when {
        mode.waterBoxMode == WaterBoxMode.OFF -> "Vacuum $fan"
        mode.fanPower == VacuumFanPower.OFF -> "Mop $water"
        else -> "Vacuum $fan, mop $water"
    }
}

/**
 * One row per room on the map; a tapped room opens its suction, water and route. Nothing is sent
 * until Save, which writes every room in one command.
 */
@Composable
private fun RoomCleanModesEditor(
    rooms: List<ParsedMapRoom>,
    stored: List<RoomCleanMode>,
    busy: Boolean,
    onSave: (List<RoomCleanMode>) -> Unit,
) {
    val saved = remember(rooms, stored) { roomCleanModesFor(rooms.map { it.id }, stored) }
    var draft by remember(saved) { mutableStateOf(saved) }
    var openSegment by remember { mutableStateOf<Int?>(null) }
    // Rooms the robot has no settings for yet still need the first save.
    val unsaved = draft != saved || rooms.any { room -> stored.none { it.segment == room.id } }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (rooms.isEmpty()) "Load the map to set up each room."
            else "Each room is cleaned with its own suction, water and route.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            rooms.forEachIndexed { index, room ->
                val mode = draft.firstOrNull { it.segment == room.id } ?: return@forEachIndexed
                val open = openSegment == room.id
                val update = { changed: RoomCleanMode ->
                    draft = draft.map { if (it.segment == room.id) changed else it }
                }
                if (index > 0) HorizontalDivider()
                Surface(onClick = { openSegment = if (open) null else room.id }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            room.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(roomModeSummary(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (open) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ModeOptionSelector("Suction power", ROOM_FAN_OPTIONS.map { it.second }, ROOM_FAN_OPTIONS.indexOfFirst { it.first == mode.fanPower }, !busy) {
                            val fan = ROOM_FAN_OPTIONS[it].first
                            // A room with neither suction nor water would not be cleaned at all.
                            val water = if (fan == VacuumFanPower.OFF && mode.waterBoxMode == WaterBoxMode.OFF) WaterBoxMode.LOW else mode.waterBoxMode
                            update(mode.copy(fanPower = fan, waterBoxMode = water))
                        }
                        ModeOptionSelector("Water flow", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(mode.waterBoxMode) }, !busy) {
                            val water = WATER_OPTIONS[it].first
                            val fan = if (water == WaterBoxMode.OFF && mode.fanPower == VacuumFanPower.OFF) VacuumFanPower.BALANCED else mode.fanPower
                            update(mode.copy(fanPower = fan, waterBoxMode = water))
                        }
                        if (mode.waterBoxMode != WaterBoxMode.OFF) {
                            ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == mode.mopMode }, !busy) {
                                update(mode.copy(mopMode = ROUTE_OPTIONS[it].first))
                            }
                        }
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            Button(onClick = { onSave(draft) }, enabled = !busy && unsaved, modifier = Modifier.fillMaxWidth()) {
                Text("Save room settings")
            }
        }
    }
}

@Composable
private fun ModeOptionSelector(label: String, options: List<String>, selectedIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val currentValue = options.getOrElse(selectedIndex) { "" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(currentValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val selected = selectedIndex == index
                Surface(
                    onClick = { if (enabled) onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(option, modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CountSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cleaning count", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(if (selected == 1) "once" else "twice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2).forEach { count ->
                val isSelected = selected == count
                Surface(onClick = { onSelect(count) }, modifier = Modifier.weight(1f), shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("×$count", modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockSheetContent(
    status: VacuumStatus,
    busy: Boolean,
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    onEmptyDustbin: () -> Unit,
    onWashMop: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
    onWashModeChange: (Int) -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            listOf("Dock control", "Dock settings").forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
            when (selectedTab) {
                0 -> DockControlTab(status, busy, onEmptyDustbin, onWashMop, onDryMop, onStopDry)
                1 -> DockSettingsTab(
                    dockSettings, dockCapabilities, status.dockType, status.dockStatus, busy,
                    onWashModeChange, onWashFrequencyChange, onAutoEmptyModeChange,
                )
            }
        }
    }
}

@Composable
private fun DockControlTab(
    status: VacuumStatus,
    busy: Boolean,
    onEmptyDustbin: () -> Unit,
    onWashMop: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
) {
    val waterAttached = status.waterBoxStatus == 1
    val dockError = status.dockErrorStatus
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DockStatusRow(
                label = "Clean water tank",
                value = when {
                    dockError == null -> "Unknown"
                    dockError == DockErrorCodes.WATER_EMPTY -> "Empty — refill"
                    else -> "OK"
                },
                color = when {
                    dockError == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    dockError == DockErrorCodes.WATER_EMPTY -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            DockStatusRow(
                label = "Dirty water tank",
                value = when (dockError) {
                    null -> "Unknown"
                    DockErrorCodes.DIRTY_TANK_FULL -> "Full — empty it"
                    DockErrorCodes.DIRTY_TANK_LATCH_OPEN -> "Latch open"
                    else -> "OK"
                },
                color = when (dockError) {
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                    DockErrorCodes.DIRTY_TANK_FULL, DockErrorCodes.DIRTY_TANK_LATCH_OPEN -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            DockStatusRow(
                label = "Dust bag",
                value = when (dockError) {
                    null -> "Unknown"
                    DockErrorCodes.NO_DUSTBIN -> "Missing"
                    DockErrorCodes.DUCT_BLOCKAGE -> "Duct blocked"
                    else -> "OK"
                },
                color = when (dockError) {
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                    DockErrorCodes.NO_DUSTBIN, DockErrorCodes.DUCT_BLOCKAGE -> MaterialTheme.colorScheme.error
                    else -> AppColors.Good
                },
            )
            if (drying) {
                DockStatusRow(
                    label = "Mop dryer",
                    value = status.remainingDryTimeSec
                        ?.takeIf { it > 0 }
                        ?.let { sec -> "Drying — ${((sec + 59) / 60)}m left" }
                        ?: "Drying",
                    color = AppColors.Warn,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockActionButton(Modifier.weight(1f), Icons.Default.Delete, AppColors.Good, "Empty", !busy, onEmptyDustbin)
            DockActionButton(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, "Wash", !busy && waterAttached, onWashMop)
            if (drying) {
                DockActionButton(Modifier.weight(1f), Icons.Default.Stop, AppColors.Warn, "Stop dry", !busy, onStopDry)
            } else {
                DockActionButton(Modifier.weight(1f), Icons.Default.Air, AppColors.Warn, "Dry", !busy, onDryMop)
            }
        }
    }
}

@Composable
private fun DockStatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DockActionButton(modifier: Modifier = Modifier, icon: ImageVector, iconTint: Color, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = if (enabled) iconTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}


@Composable
private fun DockSettingsTab(
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    dockType: Int?,
    dockStatus: Int?,
    busy: Boolean,
    onWashModeChange: (Int) -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }
    val washFrequencyOptions = dockCapabilities.washFrequencyOptions
    val smartWash = dockSettings.smartWash
    val washFrequencyIndex = washFrequencyOptions.indexOfFirst {
        smartWash != null && it.mode == smartWash.mode && (it.mode != 0 || it.intervalMinutes?.times(60) == smartWash.intervalSeconds)
    }
    val washModes = dockCapabilities.washModes(dockType, dockStatus)
    val washModeIndex = washModes.indexOfFirst { it.first == dockCapabilities.washModeCode(dockSettings.washMode, dockType) }
    val autoEmptyModes = dockCapabilities.autoEmptyModes
    val dockKnown = dockType != null && dockType != 0
    val washFrequencyEnabled = !busy && dockKnown && smartWash != null && washFrequencyOptions.isNotEmpty()
    val washModeEnabled = !busy && dockKnown && dockSettings.washMode != null && washModes.isNotEmpty()
    val autoEmptyEnabled = !busy && dockSettings.autoEmptyMode != null && autoEmptyModes.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockSettingCard(Modifier.weight(1f), Icons.Default.Repeat, AppColors.Water, "Mop wash frequency", washFrequencyLabel(smartWash), washFrequencyEnabled) { dialogTarget = "wash_freq" }
            DockSettingCard(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, "Washing mode", dockCapabilities.washModeLabel(dockSettings.washMode, dockType), washModeEnabled) { dialogTarget = "wash_mode" }
        }
        DockSettingCard(Modifier.fillMaxWidth(), Icons.Default.Delete, MaterialTheme.colorScheme.primary, "Auto-empty mode", dockCapabilities.autoEmptyModeLabel(dockSettings.autoEmptyMode), autoEmptyEnabled) { dialogTarget = "auto_empty" }
    }

    when (dialogTarget) {
        "wash_freq" -> RadioPickerDialog(
            title = "Mop wash frequency",
            options = washFrequencyOptions.map { it.label },
            selected = washFrequencyIndex,
            onSelect = { washFrequencyOptions[it].let { option -> onWashFrequencyChange(option.mode, option.intervalMinutes) } },
            enabled = washFrequencyEnabled,
            onDismiss = { dialogTarget = null },
        )
        "wash_mode" -> RadioPickerDialog(
            title = "Washing mode",
            options = washModes.map { it.second },
            selected = washModeIndex,
            onSelect = { onWashModeChange(washModes[it].first) },
            enabled = washModeEnabled,
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> RadioPickerDialog(
            title = "Auto-empty mode",
            options = autoEmptyModes.map { it.second },
            selected = autoEmptyModes.indexOfFirst { it.first == dockSettings.autoEmptyMode },
            onSelect = { onAutoEmptyModeChange(autoEmptyModes[it].first) },
            enabled = autoEmptyEnabled,
            onDismiss = { dialogTarget = null },
        )
    }
}

@Composable
private fun DockSettingCard(modifier: Modifier, icon: ImageVector, iconTint: Color, title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), iconTint) }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RadioPickerDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        RadioButton(selected = selected == index, enabled = enabled, onClick = { onSelect(index); onDismiss() })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoomsSheetContent(
    rooms: List<ParsedMapRoom>,
    selectedRoomIds: List<Int>,
    busy: Boolean,
    onRoomToggle: (Int) -> Unit,
    onCleanRooms: () -> Unit,
    onLoadMap: () -> Unit,
    onDefaultOrder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Select rooms", style = MaterialTheme.typography.headlineSmall)
        if (rooms.isEmpty()) {
            Text("Load the map first to discover your rooms.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onLoadMap, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Load map")
            }
        } else {
            val hint = if (selectedRoomIds.isEmpty())
                "Tap rooms to select — they'll be cleaned in the order you pick."
            else
                "${selectedRoomIds.size} selected — tap to reorder or remove."
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rooms.forEach { room ->
                    val order = selectedRoomIds.indexOf(room.id).takeIf { it >= 0 }?.plus(1)
                    FilterChip(
                        selected = room.id in selectedRoomIds,
                        onClick = { onRoomToggle(room.id) },
                        enabled = !busy,
                        label = {
                            if (order != null) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("$order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                    Text(room.name)
                                }
                            } else {
                                Text(room.name)
                            }
                        },
                    )
                }
            }
            Button(onClick = onCleanRooms, enabled = !busy && selectedRoomIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.extraLarge) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Clean selected rooms")
            }
            TextButton(onClick = onDefaultOrder, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Default cleaning order")
            }
        }
    }
}

/**
 * The order a full clean goes through the rooms in. Starts from the saved order, with rooms it
 * does not name added at the end; arrows move a room up or down.
 */
@Composable
private fun CleanOrderSheetContent(
    rooms: List<ParsedMapRoom>,
    savedOrder: List<Int>,
    busy: Boolean,
    onSave: (List<Int>) -> Unit,
    onRemove: () -> Unit,
) {
    val initial = remember(rooms, savedOrder) {
        val onMap = rooms.map { it.id }
        (savedOrder.filter { it in onMap } + onMap).distinct()
    }
    var order by remember(initial) { mutableStateOf(initial) }
    val names = remember(rooms) { rooms.associate { it.id to it.name } }
    fun move(from: Int, to: Int) {
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Default cleaning order", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (rooms.isEmpty()) "Load the map first to discover your rooms."
            else "Clean without picking rooms goes through them in this order. Rooms you pick are still cleaned in the order you tap them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            order.forEachIndexed { index, roomId ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        names[roomId] ?: "Room $roomId",
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { move(index, index - 1) }, enabled = !busy && index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${names[roomId] ?: "room"} up")
                    }
                    IconButton(onClick = { move(index, index + 1) }, enabled = !busy && index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${names[roomId] ?: "room"} down")
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            Button(
                onClick = { onSave(order) },
                enabled = !busy && order != savedOrder,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Save order")
            }
        }
        if (savedOrder.isNotEmpty()) {
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Remove default order")
            }
        }
    }
}


@Composable
private fun MapsSheetContent(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    loading: Boolean,
    busy: Boolean,
    blockedReason: String?,
    canAddMap: Boolean,
    unsavedMap: Boolean,
    mapSaving: Boolean?,
    multiLevel: Boolean?,
    smartSwitching: Boolean?,
    onMultiLevelChange: (Boolean) -> Unit,
    onSmartSwitchingChange: (Boolean) -> Unit,
    onEnableMapSaving: () -> Unit,
    onLoad: (Int) -> Unit,
    onRename: (FloorMap) -> Unit,
    onDelete: (FloorMap) -> Unit,
    onStartMapping: () -> Unit,
    onSaveNewMap: () -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Maps", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The maps stored on your robot. Moved the dock somewhere the map doesn't cover? " +
                "Delete the map, then let the robot map the home again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (blockedReason != null) {
            Text(blockedReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        val canManage = !busy && !loading && blockedReason == null

        // A robot that holds saved maps keeps maps, whatever its status says.
        if (mapSaving == false && floorMaps.isEmpty() && !loading) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map saving is off", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This robot does not keep a map yet, so there are no rooms, zones or floors to work with. " +
                            "Turn map saving on to let it store one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onEnableMapSaving, enabled = canManage, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn on map saving")
                    }
                }
            }
        }

        if (multiLevel != null) {
            SettingSwitchRow(
                title = "Multi-level maps",
                subtitle = "Keep up to one map per floor. Turning this off keeps one map and deletes the rest.",
                checked = multiLevel,
                enabled = canManage,
                onCheckedChange = onMultiLevelChange,
            )
            if (multiLevel && smartSwitching != null) {
                SettingSwitchRow(
                    title = "Recognise the floor automatically",
                    subtitle = "Off: you pick the map before cleaning another floor.",
                    checked = smartSwitching,
                    enabled = canManage,
                    onCheckedChange = onSmartSwitchingChange,
                )
            }
            HorizontalDivider()
        }

        if (unsavedMap) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New map not saved", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The robot has built a new map. Stop the mapping run when it has covered the home, " +
                            "then save it so it is kept and split into rooms.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onSaveNewMap, enabled = canManage, modifier = Modifier.fillMaxWidth()) {
                        Text("Save new map")
                    }
                }
            }
        }

        when {
            loading && floorMaps.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Checking robot for saved maps…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            floorMaps.isEmpty() -> {
                Text("No saved maps on the robot.", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The robot leaves the dock and drives through the home to build a new map. Stop it once " +
                        "it has covered everything, then save the map here. Open the doors to every room you want on it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onStartMapping, enabled = canManage && !unsavedMap, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Map the home")
                }
                OutlinedButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }

            else -> {
                floorMaps.forEach { floor ->
                    val selected = floor.mapFlag == currentFloorFlag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(floor.name, style = MaterialTheme.typography.bodyLarge)
                            if (selected) {
                                Text("Currently loaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { onRename(floor) }, enabled = canManage) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename ${floor.name}")
                        }
                        IconButton(onClick = { onDelete(floor) }, enabled = canManage) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete ${floor.name}",
                                tint = if (canManage) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            )
                        }
                        // Loading only matters when there is another map to switch to.
                        if (floorMaps.size > 1) {
                            Button(onClick = { onLoad(floor.mapFlag) }, enabled = !busy && !selected) {
                                Text(if (selected) "Loaded" else "Load")
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (canAddMap) {
                    OutlinedButton(onClick = onStartMapping, enabled = canManage && !unsavedMap, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Map another floor")
                    }
                }
                TextButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }
        }
    }
}

@Composable
private fun RenameMapDialog(floor: FloorMap, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(floor) { mutableStateOf(floor.name) }
    val problem = mapNameBlocker(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename map") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Map name") },
                isError = problem != null && name.isNotEmpty(),
                supportingText = { if (problem != null && name.isNotEmpty()) Text(problem) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = problem == null && name.trim() != floor.name) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Turning multi-level maps off keeps one map; the robot deletes the others (captured). */
@Composable
private fun KeepMapDialog(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var keep by remember(floorMaps) {
        mutableStateOf(floorMaps.find { it.mapFlag == currentFloorFlag }?.mapFlag ?: floorMaps.first().mapFlag)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep which map?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Single-level mode keeps one map. The robot deletes the others with their rooms " +
                        "and zones. This can't be undone.",
                )
                floorMaps.forEach { floor ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = keep == floor.mapFlag, onClick = { keep = floor.mapFlag })
                        Text(floor.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keep) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Keep this map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteMapDialog(floor: FloorMap, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete \"${floor.name}\"?") },
        text = {
            Text(
                "The robot forgets this map and everything on it: rooms, room names, floor types, " +
                    "no-go zones, walls, carpets and thresholds. This can't be undone.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
private fun batteryColor(battery: Int?): Color = when {
    battery == null -> MaterialTheme.colorScheme.onSurfaceVariant
    battery <= 15 -> MaterialTheme.colorScheme.error
    battery <= 40 -> AppColors.Warn
    else -> AppColors.Good
}

@Composable
private fun stateLabel(state: Int?): String =
    if (state == null) "—" else VacuumStateCodes.describe(state)

private fun dryingLabel(remainingDrySec: Long?): String {
    val left = remainingDrySec?.takeIf { it > 0 }?.let { s ->
        val m = s / 60
        if (m >= 60) "${m / 60}h ${m % 60}m left" else "${m}m left"
    }
    return if (left != null) "Drying mop · $left" else "Drying mop"
}


private fun isBusyState(state: Int?): Boolean = when (state) {
    null,
    VacuumStateCodes.IDLE,
    VacuumStateCodes.NOT_CHARGING,
    VacuumStateCodes.CHARGING,
    VacuumStateCodes.CHARGER_DISCONNECTED,
    VacuumStateCodes.SHUTTING_DOWN -> false
    else -> true
}

@Composable
private fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
private fun stateTint(state: Int?): Color = when (state) {
    VacuumStateCodes.WASHING_MOP, VacuumStateCodes.GOING_TO_WASH_MOP,
    VacuumStateCodes.DRYING_MOP,
    VacuumStateCodes.RETURNING_TO_DOCK_FOR_DRYING -> MaterialTheme.colorScheme.tertiary
    VacuumStateCodes.ERROR, VacuumStateCodes.CHARGING_ERROR -> MaterialTheme.colorScheme.error
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
    VacuumStateCodes.STARTING, VacuumStateCodes.MANUAL_MODE,
    VacuumStateCodes.REMOTE_CONTROL_ACTIVE, VacuumStateCodes.PAUSED,
    VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.DOCKING,
    VacuumStateCodes.GOING_TO_TARGET, VacuumStateCodes.MAPPING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
