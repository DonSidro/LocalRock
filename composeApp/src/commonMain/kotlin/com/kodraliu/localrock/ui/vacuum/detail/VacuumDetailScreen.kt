package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.http.currentEpochSeconds
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MainTab
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.SWITCH_MAP_MODE_SMART
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.appStartContinue
import com.kodraliu.localrock.shared.vacuum.appStopCollectDust
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.map.MapObstacle
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.visibleMainTabs
import com.kodraliu.localrock.ui.adaptive.LocalWindowLayout
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDock
import com.kodraliu.localrock.ui.navigation.VacuumSettingsFloor
import com.kodraliu.localrock.ui.strings.DeviceStrings
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant

/** A confirm dialog waiting on the user: official [text], then [onConfirm]. */
private class PendingConfirm(val text: String, val onConfirm: () -> Unit)

// onNavigate opens a VacuumGraph route; until the nav host passes it, the Floor and Dock settings pages fall back to the Settings root.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VacuumDetailScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onZoneClean: () -> Unit,
    onMapEdit: () -> Unit,
    onHistory: () -> Unit,
    onNavigate: ((Any) -> Unit)? = null,
) {
    val status by viewModel.status.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.repository.setLiveMapRate(true)
        onDispose { viewModel.repository.setLiveMapRate(false) }
    }
    val busy by viewModel.busy.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val deviceName by viewModel.currentDeviceName.collectAsState()
    val mapRotation by viewModel.mapRotation.collectAsState()
    val map3DYaw by viewModel.map3DYaw.collectAsState()
    val map3DTilt by viewModel.map3DTilt.collectAsState()
    val map3D by viewModel.map3D.collectAsState()
    val supportsSmartPlan by viewModel.supportsSmartPlan.collectAsState()
    val supportsWaterSlide by viewModel.supportsWaterSlide.collectAsState()
    val liveMap by viewModel.repository.parsedMap.collectAsState()
    val firstMapPending by viewModel.repository.firstMapPending.collectAsState()
    val lastKnown by viewModel.lastKnown.collectAsState()
    val lastKnownMap by viewModel.lastKnownMap.collectAsState()
    // While the robot does not answer, its last map (with where it was last seen) stands in.
    // A robot carried to its dock keeps its old map position until it re-localizes, so draw it at the charger.
    val parsedMap = remember(liveMap, lastKnownMap, status.state) { (liveMap ?: lastKnownMap)?.withRobotOnDock(isDocked(status.state)) }
    val offlineLines = remember(lastKnown) {
        lastKnown?.let { lastKnownLines(it, Instant.fromEpochSeconds(currentEpochSeconds())) } ?: emptyList()
    }
    val floorMaps by viewModel.repository.floorMaps.collectAsState()
    val currentFloorFlag by viewModel.repository.currentFloorFlag.collectAsState()
    val maxFloorMaps by viewModel.repository.maxFloorMaps.collectAsState()
    val unsavedMap by viewModel.repository.unsavedMapPresent.collectAsState()
    val mopMode by viewModel.repository.mopMode.collectAsState()
    val roomCleanModes by viewModel.repository.roomCleanModes.collectAsState()
    val routines by viewModel.routines.collectAsState()
    var selectedRoomIds by remember { mutableStateOf(emptyList<Int>()) }

    var sessionRoomIds by remember { mutableStateOf(emptyList<Int>()) }
    var panel by remember { mutableStateOf<DetailPanel?>(null) }
    var openObstacle by remember { mutableStateOf<MapObstacle?>(null) }
    var confirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var dockMenu by remember { mutableStateOf<List<DockOption>>(emptyList()) }
    val twoPane = LocalWindowLayout.current.twoPane
    BackHandler(enabled = twoPane && sidePaneShowsPanel(panel)) { panel = null }
    var recoverLoading by remember { mutableStateOf(false) }
    var recoverTick by remember { mutableIntStateOf(0) }
    var renamingMap by remember { mutableStateOf<FloorMap?>(null) }
    var deletingMap by remember { mutableStateOf<FloorMap?>(null) }
    var backingUpMap by remember { mutableStateOf<FloorMap?>(null) }
    var backingUpPicture by remember { mutableStateOf<ParsedMap?>(null) }
    var restoringMap by remember { mutableStateOf<FloorMap?>(null) }
    var restoringPicture by remember { mutableStateOf<ParsedMap?>(null) }
    var choosingMapToKeep by remember { mutableStateOf(false) }
    var lastCleanArea by remember { mutableStateOf<Long?>(null) }
    var cleaningCount by remember { mutableIntStateOf(1) }
    // The map's pan/zoom lives here so it survives the compact and two-pane switch.
    val mapViewport = remember { LiveMapViewport() }

    // Last tab survives sub-screens and rotation, like the official MC.tabIndex.
    var rememberedTab by rememberSaveable { mutableStateOf(MainTab.FULL) }
    val routineCount = (routines as? RoutinesState.Loaded)?.routines?.size ?: 0
    val tabs = visibleMainTabs(capabilities, routineCount)
    val tab = resolveTab(rememberedTab, tabs)

    val sessionRoomNames = remember(sessionRoomIds, status.state, parsedMap) {
        if (status.state == VacuumStateCodes.SEGMENT_CLEANING && sessionRoomIds.isNotEmpty()) {
            val byId = parsedMap?.rooms?.associateBy { it.id } ?: emptyMap()
            sessionRoomIds.map { id -> byId[id]?.name ?: "${DeviceStrings.TAB_ROOM} $id" }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadRoutines() }
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
    LaunchedEffect(panel == DetailPanel.Maps, recoverTick) {
        if (panel == DetailPanel.Maps) {
            recoverLoading = true
            runCatching { viewModel.repository.fetchFloorMaps() }
            recoverLoading = false
        }
    }

    val openManageMaps: () -> Unit = onMapEdit
    val onLoadMap: () -> Unit = { viewModel.refreshMap() }
    val onRecover: () -> Unit = { panel = DetailPanel.Maps }
    val toggleRoom: (Int) -> Unit = { roomId ->
        selectedRoomIds = if (roomId in selectedRoomIds) selectedRoomIds.filter { it != roomId } else selectedRoomIds + roomId
    }
    // A room tapped on the map selects it on the Room tab, whatever tab was showing.
    val onRoomTap: (Int) -> Unit = { roomId ->
        rememberedTab = MainTab.ROOM
        toggleRoom(roomId)
    }
    val onFloorSwitch: (Int) -> Unit = { flag -> viewModel.run { it.switchFloor(flag) } }
    val cleanRooms: () -> Unit = {
        val toClean = selectedRoomIds
        sessionRoomIds = toClean
        selectedRoomIds = emptyList()
        viewModel.run(DeviceStrings.CLEANING_STARTED) { it.cleanRooms(toClean, cleaningCount) }
    }
    val sendDock: () -> Unit = {
        sessionRoomIds = emptyList()
        viewModel.run(DeviceStrings.RETURNING_TOAST) { it.dock() }
    }

    val onStart: (StartAction) -> Unit = { action ->
        when (action) {
            StartAction.PAUSE -> if (pauseWaitsForDock(status.state, status.inCleaning)) viewModel.info(DeviceStrings.DOCK_WORKING_NO_PAUSE)
                else viewModel.run { it.pause() }
            StartAction.RESUME -> viewModel.run(DeviceStrings.CLEANING_RESUMED) { it.resumeCleaning() }
            StartAction.CLEAN -> when (tab) {
                MainTab.FULL -> {
                    sessionRoomIds = emptyList()
                    val order = viewModel.defaultCleanOrder()
                    viewModel.run(DeviceStrings.CLEANING_STARTED) { it.clean(order) }
                }
                MainTab.ROOM -> cleanRooms()
                MainTab.ZONE -> onZoneClean()
                MainTab.ROUTINE -> Unit
            }
        }
    }
    val onDock: () -> Unit = {
        val options = dockMenuOptions(status.state, status.inCleaning, capabilities.advertised.midwayDock, status.dockType)
        val confirmText = dockConfirm(status.state, status.inCleaning)
        when {
            options.isNotEmpty() -> dockMenu = options
            dockAction(status.state) == DockAction.OPEN_SHEET -> panel = DetailPanel.Dock
            dockAction(status.state) == DockAction.STOP_RETURNING -> if (confirmText != null) {
                // Stop returning with a job open goes back to cleaning; captured as app_start [{"clean_mop":0}] for a full clean.
                val inCleaning = status.inCleaning
                confirm = PendingConfirm(confirmText) {
                    viewModel.run(DeviceStrings.CLEANING_RESUMED) { if (inCleaning == 1) it.session.appStartContinue() else it.resumeCleaning() }
                }
            } else {
                viewModel.run { it.pause() }
            }
            confirmText != null -> confirm = PendingConfirm(confirmText, sendDock)
            else -> sendDock()
        }
    }
    val onDockOption: (DockOption) -> Unit = { option ->
        dockMenu = emptyList()
        when (option) {
            DockOption.BACK_TO_DOCK -> confirm = PendingConfirm(DeviceStrings.CONFIRM_END_CLEAN_RETURN, sendDock)
            DockOption.MIDWAY_EMPTY -> viewModel.run { it.collectDust() }
            DockOption.MIDWAY_WASH -> viewModel.run { it.washMop() }
        }
    }

    val PanelBody: @Composable (DetailPanel) -> Unit = { currentPanel ->
        when (currentPanel) {
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
                onEmptyDustbin = { viewModel.run { it.collectDust() } },
                onStopEmptying = { viewModel.run { it.session.appStopCollectDust() } },
                onWashMop = { viewModel.run { it.washMop() } },
                onStopWash = { viewModel.run { it.stopWash() } },
                onDryMop = { viewModel.run { it.startDryer() } },
                onStopDry = { viewModel.run { it.stopDryer() } },
                onDockSettings = { panel = null; onNavigate?.invoke(VacuumSettingsDock) ?: onSettings() },
            )

            DetailPanel.CleaningMode -> {
                // A refusal is shown in the sheet; the screen's own message would be behind it.
                var modeError by remember { mutableStateOf<String?>(null) }
                fun runMode(successMessage: String? = null, action: suspend (VacuumRepository) -> Unit) {
                    modeError = null
                    viewModel.run(successMessage, onError = { modeError = it }, action = action)
                }
                // Changing mode during a job ends it first, after the official confirm.
                fun changeMode(successMessage: String? = null, action: suspend (VacuumRepository) -> Unit) {
                    if (isTaskOpen(status.state, status.inCleaning)) {
                        confirm = PendingConfirm(DeviceStrings.MODE_SWITCH_CONFIRM) {
                            runMode(successMessage) { repo -> repo.stopCleaning(); action(repo) }
                        }
                    } else {
                        runMode(successMessage, action)
                    }
                }
                val seqType = status.cleanSequenceType
                val inSequence = seqType == 1
                val fan = status.fanPower?.takeUnless { it == VacuumFanPower.OFF } ?: VacuumFanPower.BALANCED
                val water = status.waterBoxCustomMode?.takeUnless { it == WaterBoxMode.OFF }
                    ?: if (supportsWaterSlide) WaterBoxMode.SLIDE_MEDIUM else WaterBoxMode.LOW
                val route = mopMode ?: MopRoute.STANDARD
                // "Vac followed by Mop" writes all three codes with app_set_clean_sequence_type; repeat rides only on type 1.
                fun writeSequence(type: Int, fan: Int, water: Int, route: Int) = changeMode {
                    it.setCleanSequenceType(type, fan, water, route, if (type == 1 && capabilities.advertised.cleanThenMopRepeat) 1 else null)
                }
                CleaningModeSheetContent(
                    currentFanPower = status.fanPower,
                    currentWaterMode = status.waterBoxCustomMode,
                    currentRoute = mopMode,
                    seqType = seqType,
                    cleaningCount = cleaningCount,
                    busy = busy,
                    supportsSmartPlan = supportsSmartPlan,
                    cleanThenMop = capabilities.advertised.cleanThenMop || inSequence,
                    pureMop = !capabilities.advertised.forbidPureMop,
                    waterSlide = supportsWaterSlide,
                    customWater = capabilities.customWaterOffset?.takeIf { !supportsWaterSlide }?.let { offset ->
                        CustomWaterUi(offset, status.distanceOff) { d -> changeMode { it.setWaterBoxDistance(d) } }
                    },
                    routeGates = capabilities.routes,
                    rooms = parsedMap?.rooms ?: emptyList(),
                    roomCleanModes = roomCleanModes,
                    roomModesLoaded = roomModesLoaded,
                    error = modeError,
                    onClose = { panel = null },
                    onPlanSelected = { plan ->
                        val (planFan, planWater, planRoute) = when (plan) {
                            CleanPlan.SMART -> Triple(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART)
                            // Water slide robots get a slide code, as python-roborock does.
                            CleanPlan.MANUAL -> Triple(VacuumFanPower.BALANCED, if (supportsWaterSlide) WaterBoxMode.SLIDE_MEDIUM else WaterBoxMode.LOW, MopRoute.STANDARD)
                            // The three codes the official app sent for its Customize tab.
                            CleanPlan.PER_ROOM -> Triple(VacuumFanPower.CUSTOM, WaterBoxMode.CUSTOM, MopRoute.CUSTOM)
                        }
                        if (inSequence) writeSequence(0, planFan, planWater, planRoute)
                        else changeMode { it.setCleanMotorMode(planFan, planWater, planRoute) }
                    },
                    onGeneralMode = { mode ->
                        val change = motorChangeFor(mode, status.fanPower, status.waterBoxCustomMode)
                        val modeFan = change.fanPower ?: fan
                        val modeWater = change.waterMode ?: water
                        when {
                            mode == GeneralMode.VAC_THEN_MOP -> {
                                writeSequence(1, modeFan, modeWater, routeForVacThenMop(route, capabilities.routes.cleanEfficiency))
                            }
                            inSequence -> writeSequence(0, modeFan, modeWater, route)
                            // The robot rejects turning suction off while water is also off, so water goes first then.
                            modeFan == VacuumFanPower.OFF -> changeMode { it.setWaterBoxMode(modeWater); it.setFanPower(modeFan) }
                            else -> changeMode { it.setFanPower(modeFan); it.setWaterBoxMode(modeWater) }
                        }
                    },
                    onSaveRoomModes = { modes -> runMode(DeviceStrings.ROOM_MODES_SAVED) { it.saveRoomCleanModes(modes) } },
                    onFanPowerSelected = { level ->
                        if (inSequence) changeMode { it.setCleanMotorMode(level, water, route) } else changeMode { it.setFanPower(level) }
                    },
                    onWaterModeSelected = { level ->
                        if (inSequence) changeMode { it.setCleanMotorMode(fan, level, route) } else changeMode { it.setWaterBoxMode(level) }
                    },
                    onRouteSelected = { mode ->
                        if (inSequence) changeMode { it.setCleanMotorMode(fan, water, mode) } else changeMode { it.setMopMode(mode) }
                    },
                    onCountChange = { cleaningCount = it },
                    onMoreSettings = { panel = null; onNavigate?.invoke(VacuumSettingsFloor) ?: onSettings() },
                )
            }

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
                onBackup = { floor, picture ->
                    backingUpPicture = picture
                    backingUpMap = floor
                },
                onRestore = { floor, picture ->
                    restoringPicture = picture
                    restoringMap = floor
                },
                loadSavedMap = { flag, backup -> viewModel.repository.fetchSavedMap(flag, backup) },
                onStartMapping = {
                    panel = null
                    viewModel.run("Mapping started") { it.startMapping() }
                },
                onSaveNewMap = { viewModel.run("New map saved") { it.saveNewMap() } },
                onRecheck = { recoverTick++ },
            )
        }
    }

    val StatusCard: @Composable () -> Unit = {
        StatusBar(
            status = status,
            lastCleanArea = lastCleanArea,
            onHistory = onHistory,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        )
    }
    val Controls: @Composable () -> Unit = {
        ControlPanel(
            tabs = tabs,
            tab = tab,
            onTab = { rememberedTab = it },
            status = status,
            busy = busy,
            modeLabel = modeChipLabel(status.fanPower, status.waterBoxCustomMode, mopMode, status.cleanSequenceType),
            onModeChip = { panel = DetailPanel.CleaningMode },
            rooms = parsedMap?.rooms ?: emptyList(),
            selectedRoomIds = selectedRoomIds,
            onRoomToggle = toggleRoom,
            onDefaultOrder = { panel = DetailPanel.Order },
            onOpenZones = onZoneClean,
            routines = routines,
            onRunRoutine = { scene -> viewModel.runRoutine(scene) },
            onRetryRoutines = { viewModel.loadRoutines() },
            onStart = onStart,
            onStop = {
                sessionRoomIds = emptyList()
                viewModel.run { it.stopCleaning() }
            },
            onDock = onDock,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).navigationBarsPadding(),
        )
    }

    Scaffold(
        topBar = {
            DeviceTopBar(
                deviceName = deviceName,
                status = status,
                mapReady = liveMap != null,
                showPetSearch = capabilities.petSearch,
                onBack = onBack,
                onMap = openManageMaps,
                onSettings = onSettings,
                onPetSearch = {
                    confirm = PendingConfirm(DeviceStrings.PET_SEARCH_CONFIRM) {
                        viewModel.run(DeviceStrings.PET_SEARCH_STARTED) { it.startPetSearch() }
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
                offlineLines = offlineLines,
                busy = busy,
                mapLoading = parsedMap == null && firstMapPending,
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
                onEditMap = openManageMaps,
                // Each view keeps its own heading: this button turns whichever one is showing.
                onRotate = { if (map3D) viewModel.turnMap3DBy(90f) else viewModel.rotateMap90() },
                onToggle3D = { viewModel.setMap3D(!map3D) },
                mapViewport = mapViewport,
                faintRoute = status.state !in ACTIVE_CLEANING_STATES && status.state != VacuumStateCodes.PAUSED,
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
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = DeviceStrings.BACK)
                                }
                                Text(headed.title(), style = MaterialTheme.typography.titleMedium)
                            }
                            PanelBody(headed)
                        } else {
                            StatusCard()
                            Spacer(Modifier.weight(1f))
                        }
                    }

                    Controls()
                }
            }
        } else {
            // The map is the screen; the compact status line and the control panel sit in a fixed band beneath it.
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues), verticalArrangement = Arrangement.Top) {
                MapHero(Modifier.weight(1f).fillMaxSize())
                StatusCard()
                Controls()
            }
        }
    }

    openObstacle?.let { obstacle ->
        ModalBottomSheet(onDismissRequest = { openObstacle = null }) {
            ObstacleSheetContent(obstacle, loadPhoto = { viewModel.repository.fetchObstaclePhoto(it) })
        }
    }

    if (!twoPane) panel?.let { current ->
        // The Maps and mode sheets have content below the fold, so they open at full height.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = current == DetailPanel.Maps || current == DetailPanel.CleaningMode)
        ModalBottomSheet(onDismissRequest = { panel = null }, sheetState = sheetState) { PanelBody(current) }
    }

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(pending.text) },
            confirmButton = {
                TextButton(onClick = { confirm = null; pending.onConfirm() }) { Text(DeviceStrings.OK) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(DeviceStrings.CANCEL) }
            },
        )
    }

    if (dockMenu.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dockMenu = emptyList() },
            title = { Text(DeviceStrings.DOCK) },
            text = {
                Column {
                    dockMenu.forEach { option ->
                        TextButton(onClick = { onDockOption(option) }, modifier = Modifier.fillMaxWidth()) {
                            Text(option.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dockMenu = emptyList() }) { Text(DeviceStrings.CANCEL) }
            },
        )
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

    backingUpMap?.let { floor ->
        BackupMapDialog(
            floor = floor,
            picture = backingUpPicture,
            onConfirm = {
                backingUpMap = null
                viewModel.run("Map backed up") { it.backupFloorMap(floor.mapFlag) }
            },
            onDismiss = { backingUpMap = null },
        )
    }

    restoringMap?.let { floor ->
        RestoreBackupDialog(
            floor = floor,
            picture = restoringPicture,
            onConfirm = {
                restoringMap = null
                viewModel.run("Map restored from backup") { it.restoreFloorMapBackup(floor.mapFlag) }
            },
            onDismiss = { restoringMap = null },
        )
    }
}
