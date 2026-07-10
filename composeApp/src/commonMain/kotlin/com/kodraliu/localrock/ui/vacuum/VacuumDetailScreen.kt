package com.kodraliu.localrock.ui.vacuum

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.map.roomAtNorm
import com.kodraliu.localrock.ui.AppColors


private val FAN_OPTIONS = listOf(
    VacuumFanPower.QUIET to "Quiet",
    VacuumFanPower.BALANCED to "Standard",
    VacuumFanPower.TURBO to "Strong",
    VacuumFanPower.MAX to "MAX",
)

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


@OptIn(ExperimentalMaterial3Api::class)
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
    val parsedMap by viewModel.repository.parsedMap.collectAsState()
    val floorMaps by viewModel.repository.floorMaps.collectAsState()
    val currentFloorFlag by viewModel.repository.currentFloorFlag.collectAsState()
    val mopMode by viewModel.repository.mopMode.collectAsState()
    val dockSettings by viewModel.repository.dockSettings.collectAsState()
    var selectedRoomIds by remember { mutableStateOf(emptyList<Int>()) }

    var sessionRoomIds by remember { mutableStateOf(emptyList<Int>()) }
    var showDockSheet by remember { mutableStateOf(false) }
    var showRoomsSheet by remember { mutableStateOf(false) }
    var showCleaningModeSheet by remember { mutableStateOf(false) }
    var showRecoverSheet by remember { mutableStateOf(false) }
    var recoverLoading by remember { mutableStateOf(false) }
    var recoverTick by remember { mutableIntStateOf(0) }
    var lastCleanArea by remember { mutableStateOf<Long?>(null) }
    var cleaningCount by remember { mutableIntStateOf(1) }


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
    LaunchedEffect(Unit) { runCatching { viewModel.repository.fetchFloorMaps() } }
    LaunchedEffect(showCleaningModeSheet) {
        if (showCleaningModeSheet) runCatching { viewModel.repository.fetchMopMode() }
    }
    LaunchedEffect(showDockSheet) {
        if (showDockSheet) runCatching { viewModel.repository.fetchDockSettings() }
    }

    LaunchedEffect(showRecoverSheet, recoverTick) {
        if (showRecoverSheet) {
            recoverLoading = true
            runCatching { viewModel.repository.fetchFloorMaps() }
            recoverLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {

                    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP
                    val busy = drying || isBusyState(status.state)
                    val accent = if (drying) MaterialTheme.colorScheme.tertiary else stateTint(status.state)
                    val label = if (drying) dryingLabel(status.remainingDryTimeSec) else stateLabel(status.state)
                    Column {
                        Text(viewModel.deviceName, style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (busy) PulsingDot(accent)
                            Text(
                                label,
                                style = if (busy) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.labelMedium,
                                fontWeight = if (busy) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (busy) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back")
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(paddingValues)) {
            val mapHeight = maxHeight * 0.55f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                StatusStrip(status = status, lastCleanArea = lastCleanArea, onHistory = onHistory)
                Spacer(Modifier.height(10.dp))

                MapHeroSection(
                    parsedMap = parsedMap,
                    height = mapHeight,
                    busy = busy,
                    selectedRoomIds = selectedRoomIds,
                    sessionRoomNames = sessionRoomNames,
                    floorMaps = floorMaps,
                    currentFloorFlag = currentFloorFlag,
                    onLoadMap = { viewModel.refreshMap() },
                    onRecover = { showRecoverSheet = true },
                    onRoomTap = { roomId ->
                        selectedRoomIds = if (roomId in selectedRoomIds)
                            selectedRoomIds.filter { it != roomId }
                        else selectedRoomIds + roomId
                    },
                    onFloorSwitch = { flag ->
                        viewModel.run { it.switchFloor(flag) }
                    },
                    onCamera = onCamera,
                    onEditZones = onMapEdit,
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickActionsRow(
                        status = status,
                        busy = busy,
                        onClean = {
                            sessionRoomIds = emptyList()
                            viewModel.run("Cleaning started") { it.clean() }
                        },
                        onPause = { viewModel.run { it.pause() } },
                        onStop = {
                            sessionRoomIds = emptyList()
                            viewModel.run { it.stopCleaning() }
                        },
                        onRooms = { showRoomsSheet = true },
                        onZoneClean = onZoneClean,
                        onSchedule = onSchedule,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = {
                                sessionRoomIds = emptyList()
                                viewModel.run("Returning to dock") { it.dock() }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Return to dock")
                        }
                        val modeLabel = when {
                            (status.waterBoxCustomMode ?: WaterBoxMode.OFF) != WaterBoxMode.OFF -> "Vac & Mop"
                            else -> "Vacuum"
                        }
                        FilledTonalButton(
                            onClick = { showCleaningModeSheet = true },
                            modifier = Modifier.height(52.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(modeLabel, style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalIconButton(
                            onClick = { showDockSheet = true },
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Dock")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRemote,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Default.Gamepad, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Remote control")
                        }
                        OutlinedButton(
                            onClick = onPinGo,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pin & Go")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showRoomsSheet) {
        ModalBottomSheet(onDismissRequest = { showRoomsSheet = false }) {
            RoomsSheetContent(
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
                    showRoomsSheet = false
                    viewModel.run { it.cleanRooms(toClean, cleaningCount) }
                },
                onLoadMap = {
                    showRoomsSheet = false
                    viewModel.refreshMap()
                },
            )
        }
    }

    if (showDockSheet) {
        ModalBottomSheet(onDismissRequest = { showDockSheet = false }) {
            DockSheetContent(
                status = status,
                busy = busy,
                dockSettings = dockSettings,
                onEmptyDustbin = { showDockSheet = false; viewModel.run { it.collectDust() } },
                onWashMop = { showDockSheet = false; viewModel.run { it.washMop() } },
                // Dry keeps the sheet open so the drying status row appears in place.
                onDryMop = { viewModel.run { it.startDryer() } },
                onStopDry = { viewModel.run { it.stopDryer() } },
                onWashModeChange = { mode -> viewModel.run { repo -> repo.setWashMode(mode) } },
                onWashFreqChange = { freq -> viewModel.run { repo -> repo.setWashFreq(freq) } },
                onAutoEmptyModeChange = { mode -> viewModel.run { repo -> repo.setAutoEmptyMode(mode) } },
            )
        }
    }

    if (showRecoverSheet) {
        ModalBottomSheet(onDismissRequest = { showRecoverSheet = false }) {
            MapRecoverySheetContent(
                floorMaps = floorMaps,
                currentFloorFlag = currentFloorFlag,
                loading = recoverLoading,
                busy = busy,
                onLoad = { flag ->
                    showRecoverSheet = false
                    viewModel.run { it.switchFloor(flag) }
                },
                onRecheck = { recoverTick++ },
            )
        }
    }

    if (showCleaningModeSheet) {
        ModalBottomSheet(onDismissRequest = { showCleaningModeSheet = false }) {
            CleaningModeSheetContent(
                currentFanPower = status.fanPower,
                currentWaterMode = status.waterBoxCustomMode,
                currentRoute = mopMode,
                cleaningCount = cleaningCount,
                busy = busy,
                onFanPowerSelected = { level -> viewModel.run { it.setFanPower(level) } },
                onWaterModeSelected = { level -> viewModel.run { it.setWaterBoxMode(level) } },
                onRouteSelected = { mode -> viewModel.run { it.setMopMode(mode) } },
                onCountChange = { cleaningCount = it },
            )
        }
    }
}


@Composable
private fun MapHeroSection(
    parsedMap: ParsedMap?,
    height: Dp,
    busy: Boolean,
    selectedRoomIds: List<Int>,
    sessionRoomNames: List<String>,
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    onLoadMap: () -> Unit,
    onRecover: () -> Unit,
    onRoomTap: (Int) -> Unit,
    onFloorSwitch: (Int) -> Unit,
    onCamera: () -> Unit,
    onEditZones: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (parsedMap != null) {
            VacuumMapImage(
                map = parsedMap,
                selectedRoomIds = selectedRoomIds,
                onRoomTap = onRoomTap,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MapEmptyState(busy = busy, onLoadMap = onLoadMap, onRecover = onRecover, modifier = Modifier.fillMaxSize())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth().height(72.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))),
        )

        FilledTonalIconButton(
            onClick = onLoadMap,
            enabled = !busy,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, contentDescription = "Refresh map", modifier = Modifier.size(20.dp))
        }

        FilledTonalIconButton(
            onClick = onCamera,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Default.Videocam, contentDescription = "Camera live view")
        }

        if (parsedMap != null) {
            FilledTonalIconButton(
                onClick = onEditZones,
                modifier = Modifier.align(Alignment.CenterEnd).padding(10.dp).size(40.dp),
            ) {
                Icon(Icons.Default.Block, contentDescription = "Edit no-go zones", modifier = Modifier.size(20.dp))
            }
        }

        FilledTonalIconButton(
            onClick = onRecover,
            enabled = !busy,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(40.dp),
        ) {
            Icon(Icons.Default.Restore, contentDescription = "Recover saved map", modifier = Modifier.size(20.dp))
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (floorMaps.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    floorMaps.forEach { floor ->
                        val selected = floor.mapFlag == currentFloorFlag
                        Surface(
                            onClick = { if (!selected) onFloorSwitch(floor.mapFlag) },
                            shape = MaterialTheme.shapes.small,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.55f),
                        ) {
                            Text(
                                text = floor.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                            )
                        }
                    }
                }
            }
            if (sessionRoomNames.isNotEmpty()) {
                SessionRoomsBadge(roomNames = sessionRoomNames)
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

@Composable
private fun VacuumMapImage(
    map: ParsedMap,
    selectedRoomIds: List<Int>,
    onRoomTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberMapBitmap(map)
    val aspectRatio = map.width.toFloat() / map.height.toFloat()
    val labelRooms = remember(map.rooms) { map.rooms.filter { it.labelNormX != null && it.labelNormY != null } }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val bgColor = Color.Black.copy(alpha = 0.5f)
    val selectedBgColor = MaterialTheme.colorScheme.primary
    val currentOnRoomTap by rememberUpdatedState(onRoomTap)

    var mapScale by remember { mutableStateOf(1f) }
    var mapOffset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier

                .aspectRatio(aspectRatio)
                .clipToBounds()
                .pointerInput(map) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (mapScale * zoom).coerceIn(1f, 8f)
                        mapOffset = centroid - (centroid - mapOffset) * (newScale / mapScale) + pan
                        // Clamp so the map can't be panned fully off-screen
                        val maxX = size.width * (newScale - 1f)
                        val maxY = size.height * (newScale - 1f)
                        mapOffset = Offset(
                            mapOffset.x.coerceIn(-maxX, 0f),
                            mapOffset.y.coerceIn(-maxY, 0f),
                        )
                        mapScale = newScale
                    }
                }
                .pointerInput(map) {
                    detectTapGestures(
                        onDoubleTap = {
                            mapScale = 1f
                            mapOffset = Offset.Zero
                        },
                        onTap = { tapOffset ->
                            val mapX = (tapOffset.x - mapOffset.x) / mapScale
                            val mapY = (tapOffset.y - mapOffset.y) / mapScale
                            map.roomAtNorm(mapX / size.width, mapY / size.height)
                                ?.let { currentOnRoomTap(it.id) }
                        },
                    )
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
                for (room in labelRooms) {
                    val cx = (room.labelNormX ?: continue) * size.width
                    val cy = (room.labelNormY ?: continue) * size.height
                    val bg = if (room.id in selectedRoomIds) selectedBgColor else bgColor
                    val measured = textMeasurer.measure(room.name, labelStyle)
                    val tw = measured.size.width.toFloat()
                    val th = measured.size.height.toFloat()
                    val pad = 3.dp.toPx()
                    drawRoundRect(
                        color = bg,
                        topLeft = Offset(cx - tw / 2 - pad, cy - th / 2 - pad),
                        size = Size(tw + pad * 2, th + pad * 2),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = room.name,
                        style = labelStyle,
                        topLeft = Offset(cx - tw / 2, cy - th / 2),
                    )
                }
            }
        }

        if (mapScale > 1.05f) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                color = Color.Black.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    "${(mapScale * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
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
            Text("Recover saved map")
        }
    }
}


@Composable
private fun StatusStrip(status: VacuumStatus, lastCleanArea: Long?, onHistory: () -> Unit) {
    // clean_area is reported in mm²; show m² with one decimal.
    val area = lastCleanArea?.let { mm2 ->
        val tenths = mm2 / 100_000
        "${tenths / 10}.${tenths % 10}"
    } ?: "—"
    val time = status.cleanTime?.let { s ->
        val m = s / 60
        if (m < 60) "${m}m" else "${m / 60}h ${m % 60}m"
    } ?: "—"

    val cleaningNow = status.state in ACTIVE_CLEANING_STATES
    val charging = status.chargeStatus == 1 || status.state == VacuumStateCodes.CHARGING
    val battery = status.battery
    val batteryIcon = when {
        charging -> Icons.Default.BatteryChargingFull
        battery == null -> Icons.Default.BatteryUnknown
        battery >= 95 -> Icons.Default.BatteryFull
        battery >= 80 -> Icons.Default.Battery6Bar
        battery >= 65 -> Icons.Default.Battery5Bar
        battery >= 50 -> Icons.Default.Battery4Bar
        battery >= 35 -> Icons.Default.Battery3Bar
        battery >= 20 -> Icons.Default.Battery2Bar
        else -> Icons.Default.Battery1Bar
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = batteryIcon,
            iconTint = batteryColor(battery),
            value = battery?.let { "$it%" } ?: "—",
            label = if (charging) "Charging" else "Battery",
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.SelectAll,
            iconTint = MaterialTheme.colorScheme.primary,
            value = area,
            label = if (cleaningNow) "Cleaning now" else "Last clean",
            unit = "m²",
            onClick = onHistory,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timer,
            iconTint = MaterialTheme.colorScheme.tertiary,
            value = time,
            label = if (cleaningNow) "Elapsed" else "Duration",
            onClick = onHistory,
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    unit: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val unitColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
            Text(
                text = buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = unitColor)) {
                            append(" $unit")
                        }
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun QuickActionsRow(
    status: VacuumStatus,
    busy: Boolean,
    onClean: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onRooms: () -> Unit,
    onZoneClean: () -> Unit,
    onSchedule: () -> Unit,
) {
    val isCleaning = status.state in ACTIVE_CLEANING_STATES
    val isPaused = status.state == VacuumStateCodes.PAUSED
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionButton(Modifier.weight(1f), if (isCleaning) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isCleaning) "Pause" else if (isPaused) "Resume" else "Clean", !busy, primary = true) {
            if (isCleaning) onPause() else onClean()
        }
        if (isCleaning || isPaused) {
            QuickActionButton(Modifier.weight(1f), Icons.Default.Stop, "Stop", !busy, onClick = onStop)
        }
        QuickActionButton(Modifier.weight(1f), Icons.Default.GridView, "Rooms", !busy, onClick = onRooms)
        QuickActionButton(Modifier.weight(1f), Icons.Default.SelectAll, "Zones", !busy, onClick = onZoneClean)
        QuickActionButton(Modifier.weight(1f), Icons.Default.Schedule, "Routine", !busy, onClick = onSchedule)
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Column(modifier, Arrangement.spacedBy(8.dp), Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick, enabled = enabled, modifier = Modifier.size(60.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when { !enabled -> MaterialTheme.colorScheme.surfaceVariant; primary -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.secondaryContainer },
                contentColor = when { !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f); primary -> MaterialTheme.colorScheme.onPrimary; else -> MaterialTheme.colorScheme.onSecondaryContainer },
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        ) { Icon(icon, label, Modifier.size(26.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center)
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

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Spacer(Modifier.height(12.dp))
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
                    ModeOptionSelector("Suction Power", FAN_OPTIONS.map { it.second }, FAN_OPTIONS.indexOfFirst { it.first == currentFanPower }.coerceAtLeast(0), !busy) { onFanPowerSelected(FAN_OPTIONS[it].first) }
                }
                val water = @Composable {
                    ModeOptionSelector("Water Flow", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == currentWaterMode }.coerceAtLeast(0), !busy) { onWaterModeSelected(WATER_OPTIONS[it].first) }
                }
                val route = @Composable {
                    ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == currentRoute }.coerceAtLeast(0), !busy) { onRouteSelected(ROUTE_OPTIONS[it].first) }
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
    onEmptyDustbin: () -> Unit,
    onWashMop: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
    onWashModeChange: (Int) -> Unit,
    onWashFreqChange: (Int) -> Unit,
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
                1 -> DockSettingsTab(dockSettings, onWashModeChange, onWashFreqChange, onAutoEmptyModeChange)
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
    onWashModeChange: (Int) -> Unit,
    onWashFreqChange: (Int) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }
    val washFreqOptions = listOf("Every trip", "Every 2 trips", "Every 3 trips")
    val washModeOptions = listOf("Light", "Standard", "Intensive")
    val autoEmptyOptions = listOf("Smart", "Max suction", "Normal")

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockSettingCard(Modifier.weight(1f), Icons.Default.Repeat, AppColors.Water, "Mop Wash Frequency", washFreqOptions.getOrElse(dockSettings.washFreq) { "Every trip" }) { dialogTarget = "wash_freq" }
            DockSettingCard(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, "Washing Mode", washModeOptions.getOrElse(dockSettings.washMode) { "Standard" }) { dialogTarget = "wash_mode" }
        }
        DockSettingCard(Modifier.fillMaxWidth(), Icons.Default.Delete, MaterialTheme.colorScheme.primary, "Auto-Empty Mode", autoEmptyOptions.getOrElse(dockSettings.autoEmptyMode) { "Smart" }) { dialogTarget = "auto_empty" }
    }

    when (dialogTarget) {
        "wash_freq" -> RadioPickerDialog("Mop wash frequency", washFreqOptions, dockSettings.washFreq, onWashFreqChange) { dialogTarget = null }
        "wash_mode" -> RadioPickerDialog("Washing mode", washModeOptions, dockSettings.washMode, onWashModeChange) { dialogTarget = null }
        "auto_empty" -> RadioPickerDialog("Auto-empty mode", autoEmptyOptions, dockSettings.autoEmptyMode, onAutoEmptyModeChange) { dialogTarget = null }
    }
}

@Composable
private fun DockSettingCard(modifier: Modifier, icon: ImageVector, iconTint: Color, title: String, value: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = CircleShape, color = iconTint.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), iconTint) }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RadioPickerDialog(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        RadioButton(selected = selected == index, onClick = { onSelect(index); onDismiss() })
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
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
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
        }
    }
}


@Composable
private fun MapRecoverySheetContent(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    loading: Boolean,
    busy: Boolean,
    onLoad: (Int) -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Recover saved map", style = MaterialTheme.typography.headlineSmall)
        Text(
            "These are the maps still stored on your robot. Loading one re-activates it. " +
                "If the list is empty, the map can't be restored from here — run a full clean to let the robot rebuild it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Checking robot for saved maps…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            floorMaps.isEmpty() -> {
                Text(
                    "No saved maps found on the robot.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }

            else -> {
                floorMaps.forEach { floor ->
                    val selected = floor.mapFlag == currentFloorFlag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(floor.name, style = MaterialTheme.typography.bodyLarge)
                            if (selected) {
                                Text("Currently loaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Button(onClick = { onLoad(floor.mapFlag) }, enabled = !busy && !selected) {
                            Text(if (selected) "Loaded" else "Load")
                        }
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }
        }
    }
}


@Composable
private fun batteryColor(battery: Int?): Color = when {
    battery == null -> MaterialTheme.colorScheme.onSurfaceVariant
    battery <= 15 -> MaterialTheme.colorScheme.error
    battery <= 40 -> AppColors.Warn
    else -> AppColors.Good
}

@Composable
private fun stateLabel(state: Int?): String = when (state) {
    null -> "—"
    VacuumStateCodes.STARTING -> "Starting"
    VacuumStateCodes.CHARGER_DISCONNECTED -> "Charger disconnected"
    VacuumStateCodes.IDLE -> "Idle"
    VacuumStateCodes.REMOTE_CONTROL_ACTIVE -> "Remote control"
    VacuumStateCodes.CLEANING -> "Cleaning"
    VacuumStateCodes.RETURNING_HOME -> "Returning home"
    VacuumStateCodes.MANUAL_MODE -> "Manual mode"
    VacuumStateCodes.CHARGING -> "Charging"
    VacuumStateCodes.CHARGING_ERROR -> "Charging error"
    VacuumStateCodes.PAUSED -> "Paused"
    VacuumStateCodes.SPOT_CLEANING -> "Spot cleaning"
    VacuumStateCodes.ERROR -> "Error"
    VacuumStateCodes.SHUTTING_DOWN -> "Shutting down"
    VacuumStateCodes.UPDATING -> "Updating"
    VacuumStateCodes.DOCKING -> "Docking"
    VacuumStateCodes.GOING_TO_TARGET -> "Going to target"
    VacuumStateCodes.ZONED_CLEANING -> "Zoned cleaning"
    VacuumStateCodes.SEGMENT_CLEANING -> "Room cleaning"
    VacuumStateCodes.MAPPING -> "Mapping"
    VacuumStateCodes.NOT_CHARGING -> "Idle"
    VacuumStateCodes.WASHING_MOP -> "Washing mop"
    VacuumStateCodes.GOING_TO_WASH_MOP -> "Going to wash mop"
    VacuumStateCodes.DRYING_MOP -> "Drying mop"
    VacuumStateCodes.RETURNING_TO_DOCK_FOR_DRYING -> "Returning to dock"
    VacuumStateCodes.RETURNING_TO_WASH_MOP -> "Returning to wash mop"
    else -> "State $state"
}

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
    VacuumStateCodes.RETURNING_TO_WASH_MOP, VacuumStateCodes.DRYING_MOP,
    VacuumStateCodes.RETURNING_TO_DOCK_FOR_DRYING -> MaterialTheme.colorScheme.tertiary
    VacuumStateCodes.ERROR, VacuumStateCodes.CHARGING_ERROR -> MaterialTheme.colorScheme.error
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
    VacuumStateCodes.STARTING, VacuumStateCodes.MANUAL_MODE,
    VacuumStateCodes.REMOTE_CONTROL_ACTIVE, VacuumStateCodes.PAUSED,
    VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.DOCKING,
    VacuumStateCodes.GOING_TO_TARGET -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
