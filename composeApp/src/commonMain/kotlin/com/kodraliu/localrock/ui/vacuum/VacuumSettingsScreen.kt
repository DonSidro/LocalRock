package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanSummary
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.DndSettings
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.RobotSettings
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.AppColors
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// ── Navigation ────────────────────────────────────────────────────────────────

private sealed interface SettingsRoute {
    data object Root : SettingsRoute
    data object Maintenance : SettingsRoute
    data object DockSettings : SettingsRoute
    data object RobotSettings : SettingsRoute
    data object DndMode : SettingsRoute
    data object History : SettingsRoute
}

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacuumSettingsScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    var route by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }
    val scope = rememberCoroutineScope()
    val consumables by repository.consumableStatus.collectAsState()
    val robotSettings by repository.robotSettings.collectAsState()
    val dockSettings by repository.dockSettings.collectAsState()
    val dndSettings by repository.dndSettings.collectAsState()
    val cleanHistory by repository.cleanHistory.collectAsState()
    val cleanSummary by repository.cleanSummary.collectAsState()
    val firmwareUpdate by viewModel.firmwareUpdate.collectAsState()

    when (route) {
        SettingsRoute.Root -> SettingsRootScreen(
            modelName = viewModel.modelName,
            firmwareVersion = viewModel.firmwareVersion,
            firmwareUpdate = firmwareUpdate,
            onCheckUpdate = { viewModel.checkFirmwareUpdate() },
            onBack = onBack,
            onHistory = { route = SettingsRoute.History },
            onMaintenance = {
                scope.launch { runCatching { repository.fetchConsumableStatus() } }
                route = SettingsRoute.Maintenance
            },
            onDockSettings = {
                scope.launch { runCatching { repository.fetchDockSettings() } }
                route = SettingsRoute.DockSettings
            },
            onRobotSettings = {
                scope.launch { runCatching { repository.fetchRobotSettings() } }
                route = SettingsRoute.RobotSettings
            },
            onDndMode = {
                scope.launch { runCatching { repository.fetchDndSettings() } }
                route = SettingsRoute.DndMode
            },
        )
        SettingsRoute.Maintenance -> MaintenanceScreen(
            consumables = consumables,
            onRefresh = { scope.launch { runCatching { repository.fetchConsumableStatus() } } },
            onReset = { field -> scope.launch { runCatching { repository.resetConsumable(field) } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.DockSettings -> DockSettingsScreen(
            dockSettings = dockSettings,
            onWashModeChange = { scope.launch { runCatching { repository.setWashMode(it) } } },
            onWashFreqChange = { scope.launch { runCatching { repository.setWashFreq(it) } } },
            onAutoEmptyModeChange = { scope.launch { runCatching { repository.setAutoEmptyMode(it) } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.RobotSettings -> RobotSettingsScreen(
            robotSettings = robotSettings,
            onFindRobot = { scope.launch { runCatching { repository.findMe() } } },
            onVolumeChange = { scope.launch { runCatching { repository.setVolume(it) } } },
            onChildLockChange = { scope.launch { runCatching { repository.setChildLock(it) } } },
            onLedChange = { scope.launch { runCatching { repository.setLed(it) } } },
            onCarpetModeChange = { scope.launch { runCatching { repository.setCarpetMode(it) } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.DndMode -> DndScreen(
            dndSettings = dndSettings,
            onEnable = { sh, sm, eh, em -> scope.launch { runCatching { repository.setDnd(sh, sm, eh, em) } } },
            onDisable = { scope.launch { runCatching { repository.disableDnd() } } },
            onBack = { route = SettingsRoute.Root },
        )
        SettingsRoute.History -> {

            LaunchedEffect(Unit) {
                runCatching { repository.fetchCleanHistory() }
                runCatching { repository.fetchCleanSummary() }
            }
            HistoryScreen(
                records = cleanHistory,
                summary = cleanSummary,
                onRefresh = {
                    scope.launch {
                        runCatching { repository.fetchCleanHistory() }
                        runCatching { repository.fetchCleanSummary() }
                    }
                },
                onBack = { route = SettingsRoute.Root },
            )
        }
    }
}


@Composable
fun VacuumRemoteScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { repository.startRemoteControl() } }
    RemoteControlScreen(
        onMove = { v, o -> scope.launch { runCatching { repository.moveRemote(v, o, 1500) } } },
        onStop = { scope.launch { runCatching { repository.stopRemoteControl() } } },
        onBack = {
            scope.launch { runCatching { repository.stopRemoteControl() } }
            onBack()
        },
    )
}

@Composable
fun VacuumPinGoScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val parsedMap by repository.parsedMap.collectAsState()
    PinGoScreen(
        parsedMap = parsedMap,
        onGoto = { x, y -> scope.launch { runCatching { repository.gotoTarget(x, y) } } },
        onBack = onBack,
    )
}

@Composable
fun VacuumHistoryScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val cleanHistory by repository.cleanHistory.collectAsState()
    val cleanSummary by repository.cleanSummary.collectAsState()
    LaunchedEffect(Unit) {
        runCatching { repository.fetchCleanHistory() }
        runCatching { repository.fetchCleanSummary() }
    }
    HistoryScreen(
        records = cleanHistory,
        summary = cleanSummary,
        onRefresh = {
            scope.launch {
                runCatching { repository.fetchCleanHistory() }
                runCatching { repository.fetchCleanSummary() }
            }
        },
        onBack = onBack,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRootScreen(
    modelName: String?,
    firmwareVersion: String?,
    firmwareUpdate: FirmwareUpdateState,
    onCheckUpdate: () -> Unit,
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onMaintenance: () -> Unit,
    onDockSettings: () -> Unit,
    onRobotSettings: () -> Unit,
    onDndMode: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                SettingsCategoryRow(
                    icon = Icons.Default.History,
                    iconTint = AppColors.AccentCyan,
                    title = "Cleaning History",
                    subtitle = "Past cleaning sessions and totals",
                    onClick = onHistory,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingsCategoryRow(
                    icon = Icons.Default.Build,
                    iconTint = AppColors.Good,
                    title = "Maintenance",
                    subtitle = "Brush life and consumables",
                    onClick = onMaintenance,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingsCategoryRow(
                    icon = Icons.Default.Home,
                    iconTint = AppColors.Water,
                    title = "Dock Settings",
                    subtitle = "Auto-empty, wash and dry options",
                    onClick = onDockSettings,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingsCategoryRow(
                    icon = Icons.Default.Settings,
                    iconTint = AppColors.AccentPurple,
                    title = "Robot Settings",
                    subtitle = "Volume, child lock and more",
                    onClick = onRobotSettings,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingsCategoryRow(
                    icon = Icons.Default.Schedule,
                    iconTint = AppColors.AccentIndigo,
                    title = "Do Not Disturb",
                    subtitle = "Set quiet hours for cleaning",
                    onClick = onDndMode,
                )
            }

            item {
                Text(
                    "Firmware",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            item {
                SettingRowInfo(
                    icon = Icons.Default.Router,
                    iconTint = AppColors.AccentIndigo,
                    title = "Model",
                    value = modelName ?: "Unknown",
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowInfo(
                    icon = Icons.Default.Memory,
                    iconTint = AppColors.AccentCyan,
                    title = "Firmware version",
                    value = firmwareVersion ?: "Unknown",
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                FirmwareUpdateRow(state = firmwareUpdate, onCheck = onCheckUpdate)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DndScreen(
    dndSettings: DndSettings?,
    onEnable: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = dndSettings ?: DndSettings()
    var enabled by remember(dndSettings?.enabled) { mutableStateOf(settings.enabled) }
    var startHour by remember(dndSettings) { mutableStateOf(settings.startHour) }
    var startMinute by remember(dndSettings) { mutableStateOf(settings.startMinute) }
    var endHour by remember(dndSettings) { mutableStateOf(settings.endHour) }
    var endMinute by remember(dndSettings) { mutableStateOf(settings.endMinute) }

    fun formatTime(h: Int, m: Int) =
        "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    fun commit() = if (enabled) onEnable(startHour, startMinute, endHour, endMinute) else onDisable()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Do Not Disturb") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable quiet hours", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (enabled) "${formatTime(startHour, startMinute)} – ${formatTime(endHour, endMinute)}"
                        else "Robot will clean anytime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; commit() },
                )
            }

            if (enabled) {
                HorizontalDivider()
                Text("Start time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberStepper2(label = "Hour", value = startHour, range = 0..23, onValueChange = { startHour = it; commit() })
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    NumberStepper2(label = "Minute", value = startMinute, range = 0..59, step = 15, onValueChange = { startMinute = it; commit() })
                }

                Text("End time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberStepper2(label = "Hour", value = endHour, range = 0..23, onValueChange = { endHour = it; commit() })
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    NumberStepper2(label = "Minute", value = endMinute, range = 0..59, step = 15, onValueChange = { endMinute = it; commit() })
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "Robot won't start cleaning between ${formatTime(startHour, startMinute)} and ${formatTime(endHour, endMinute)}.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberStepper2(label: String, value: Int, range: IntRange, step: Int = 1, onValueChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { val next = value - step; onValueChange(if (next < range.first) range.last - (range.last % step) else next) },
                    modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Default.KeyboardArrowLeft, null, Modifier.size(20.dp)) }
                Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                IconButton(
                    onClick = { val next = value + step; onValueChange(if (next > range.last) range.first else next) },
                    modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp), iconTint)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceScreen(
    consumables: ConsumableStatus?,
    onRefresh: () -> Unit,
    onReset: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maintenance") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        if (consumables == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.Build,
                        null,
                        Modifier.size(48.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Could not load consumable data", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onRefresh) { Text("Retry") }
                }
            }
        } else {
            LazyColumn(contentPadding = padding) {
                item {
                    ConsumableItem(
                        icon = Icons.Default.Cached,
                        iconTint = AppColors.Good,
                        title = "Main Brush",
                        usedSeconds = consumables.mainBrushWorkTime ?: 0L,
                        maxSeconds = ConsumableStatus.MAIN_BRUSH_LIFETIME_S,
                        onReset = { onReset("main_brush_work_time") },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                item {
                    ConsumableItem(
                        icon = Icons.Default.Autorenew,
                        iconTint = AppColors.Water,
                        title = "Side Brush",
                        usedSeconds = consumables.sideBrushWorkTime ?: 0L,
                        maxSeconds = ConsumableStatus.SIDE_BRUSH_LIFETIME_S,
                        onReset = { onReset("side_brush_work_time") },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                item {
                    ConsumableItem(
                        icon = Icons.Default.Air,
                        iconTint = AppColors.AccentPurple,
                        title = "Filter",
                        usedSeconds = consumables.filterWorkTime ?: 0L,
                        maxSeconds = ConsumableStatus.FILTER_LIFETIME_S,
                        onReset = { onReset("filter_work_time") },
                    )
                }
                item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                item {
                    ConsumableItem(
                        icon = Icons.Default.Sensors,
                        iconTint = AppColors.Warn,
                        title = "Sensor",
                        usedSeconds = consumables.sensorDirtyTime ?: 0L,
                        maxSeconds = ConsumableStatus.SENSOR_LIFETIME_S,
                        onReset = { onReset("sensor_dirty_time") },
                    )
                }
                consumables.cleaningBrushWorkTime?.let { used ->
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        ConsumableItem(
                            icon = Icons.Default.CleaningServices,
                            iconTint = AppColors.AccentCyan,
                            title = "Maintenance Brush",
                            usedSeconds = used,
                            maxSeconds = ConsumableStatus.CLEANING_BRUSH_LIFETIME_S,
                            onReset = { onReset("cleaning_brush_work_times") },
                        )
                    }
                }
                consumables.strainerWorkTime?.let { used ->
                    item { HorizontalDivider(Modifier.padding(start = 76.dp)) }
                    item {
                        ConsumableItem(
                            icon = Icons.Default.FilterAlt,
                            iconTint = AppColors.AccentIndigo,
                            title = "Strainer",
                            usedSeconds = used,
                            maxSeconds = ConsumableStatus.STRAINER_LIFETIME_S,
                            onReset = { onReset("strainer_work_times") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsumableItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    usedSeconds: Long,
    maxSeconds: Long,
    onReset: () -> Unit,
) {
    val remaining = (maxSeconds - usedSeconds).coerceAtLeast(0L)
    val fraction = (remaining.toFloat() / maxSeconds).coerceIn(0f, 1f)
    val remainingHours = remaining / 3600L
    val remainingMinutes = (remaining % 3600L) / 60L
    val percent = (fraction * 100).toInt()
    val progressColor = when {
        fraction > 0.5f -> AppColors.Good
        fraction > 0.2f -> AppColors.Warn
        else -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp), iconTint)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("$percent%", style = MaterialTheme.typography.labelMedium, color = progressColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                if (remainingHours > 0) "${remainingHours}h ${remainingMinutes}m remaining"
                else "${remainingMinutes}m remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) {
            Text("Reset", color = MaterialTheme.colorScheme.error)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    records: List<CleanRecord>,
    summary: CleanSummary?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Default.History, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No history yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Complete a cleaning session to see it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onRefresh) { Text("Load history") }
                }
            }
        } else {
            LazyColumn(contentPadding = padding) {
                if (summary != null) {
                    item { CleanSummaryCard(summary) }
                }
                items(records.sortedByDescending { it.beginEpoch }, key = { it.beginEpoch }) { record ->
                    CleanRecordRow(record)
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

@Composable
private fun CleanSummaryCard(summary: CleanSummary) {
    val totalHours = summary.totalTimeSec / 3600
    val totalMinutes = (summary.totalTimeSec % 3600) / 60
    val totalAreaM2 = summary.totalAreaMm2 / 1_000_000f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lifetime Stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStatItem("${summary.totalCount}", "Sessions")
                SummaryStatItem("${totalAreaM2.roundToInt()} m²", "Total area")
                SummaryStatItem(
                    if (totalHours > 0) "${totalHours}h ${totalMinutes}m" else "${totalMinutes}m",
                    "Total time",
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CleanRecordRow(record: CleanRecord) {
    val areaM2 = record.areaMm2 / 1_000_000f
    val durationMin = record.durationSeconds / 60L
    val dateStr = formatCleanRecordTime(record.beginEpoch)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (record.complete == 1) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (record.complete == 1) Icons.Default.CheckCircle else Icons.Default.Warning,
                    null,
                    Modifier.size(22.dp),
                    if (record.complete == 1) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(dateStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(oneDecimal(areaM2)) }
                        append(" m²")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("$durationMin") }
                        append(" min")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockSettingsScreen(
    dockSettings: DockSettings,
    onWashModeChange: (Int) -> Unit,
    onWashFreqChange: (Int) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }

    val washFreqOptions = listOf("Every trip", "Every 2 trips", "Every 3 trips")
    val washModeOptions = listOf("Light", "Standard", "Intensive")
    val autoEmptyOptions = listOf("Smart", "Max suction", "Normal")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dock Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DockSettingCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    iconTint = AppColors.Water,
                    title = "Mop Wash Freq",
                    value = washFreqOptions.getOrElse(dockSettings.washFreq) { "Every trip" },
                    onClick = { dialogTarget = "wash_freq" },
                )
                DockSettingCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.WaterDrop,
                    iconTint = AppColors.AccentCyan,
                    title = "Washing Mode",
                    value = washModeOptions.getOrElse(dockSettings.washMode) { "Standard" },
                    onClick = { dialogTarget = "wash_mode" },
                )
            }
            DockSettingCard(
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.Delete,
                iconTint = AppColors.Good,
                title = "Auto-Empty Mode",
                value = autoEmptyOptions.getOrElse(dockSettings.autoEmptyMode) { "Smart" },
                onClick = { dialogTarget = "auto_empty" },
            )
        }
    }

    when (dialogTarget) {
        "wash_freq" -> SimplePickerDialog(
            title = "Mop Wash Frequency",
            options = washFreqOptions,
            selected = dockSettings.washFreq,
            onSelect = { onWashFreqChange(it); dialogTarget = null },
            onDismiss = { dialogTarget = null },
        )
        "wash_mode" -> SimplePickerDialog(
            title = "Washing Mode",
            options = washModeOptions,
            selected = dockSettings.washMode,
            onSelect = { onWashModeChange(it); dialogTarget = null },
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> SimplePickerDialog(
            title = "Auto-Empty Mode",
            options = autoEmptyOptions,
            selected = dockSettings.autoEmptyMode,
            onSelect = { onAutoEmptyModeChange(it); dialogTarget = null },
            onDismiss = { dialogTarget = null },
        )
    }
}

@Composable
private fun DockSettingCard(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), iconTint)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    null,
                    Modifier.size(14.dp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SimplePickerDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = { onSelect(index) })
                        Spacer(Modifier.width(8.dp))
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RobotSettingsScreen(
    robotSettings: RobotSettings,
    onFindRobot: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onChildLockChange: (Boolean) -> Unit,
    onLedChange: (Boolean) -> Unit,
    onCarpetModeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var findTriggered by remember { mutableStateOf(false) }
    var showVolumeDialog by remember { mutableStateOf(false) }
    var showCarpetDialog by remember { mutableStateOf(false) }

    val volumeOptions = listOf(0 to "Off", 30 to "Low", 60 to "Medium", 100 to "High")
    val carpetOptions = listOf("Avoid carpet", "Standard", "Carpet boost")

    val volumeLabel = volumeOptions.minByOrNull { kotlin.math.abs(it.first - robotSettings.volume) }?.second ?: "Medium"
    val carpetLabel = carpetOptions.getOrElse(robotSettings.carpetMode) { "Standard" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Robot Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                SettingRowClickable(
                    icon = Icons.Default.Sensors,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "Find Robot",
                    subtitle = if (findTriggered) "Beeping…" else "Make the robot beep to locate it",
                    onClick = { onFindRobot(); findTriggered = true },
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowClickable(
                    icon = Icons.Default.VolumeUp,
                    iconTint = AppColors.Water,
                    title = "Volume",
                    subtitle = volumeLabel,
                    onClick = { showVolumeDialog = true },
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowSwitch(
                    icon = Icons.Default.Lock,
                    iconTint = AppColors.Warn,
                    title = "Child Lock",
                    subtitle = if (robotSettings.childLock) "Buttons locked on robot" else "Buttons active on robot",
                    checked = robotSettings.childLock,
                    onCheckedChange = onChildLockChange,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowSwitch(
                    icon = Icons.Default.Lightbulb,
                    iconTint = AppColors.AccentYellow,
                    title = "Status LED",
                    subtitle = if (robotSettings.ledEnabled) "LED indicator on" else "LED indicator off",
                    checked = robotSettings.ledEnabled,
                    onCheckedChange = onLedChange,
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowClickable(
                    icon = Icons.Default.GridView,
                    iconTint = AppColors.Good,
                    title = "Carpet Mode",
                    subtitle = carpetLabel,
                    onClick = { showCarpetDialog = true },
                )
            }
        }
    }

    if (showVolumeDialog) {
        val selectedIndex = volumeOptions.indexOfFirst { it.second == volumeLabel }.coerceAtLeast(2)
        SimplePickerDialog(
            title = "Volume",
            options = volumeOptions.map { it.second },
            selected = selectedIndex,
            onSelect = { onVolumeChange(volumeOptions[it].first); showVolumeDialog = false },
            onDismiss = { showVolumeDialog = false },
        )
    }
    if (showCarpetDialog) {
        SimplePickerDialog(
            title = "Carpet Mode",
            options = carpetOptions,
            selected = robotSettings.carpetMode,
            onSelect = { onCarpetModeChange(it); showCarpetDialog = false },
            onDismiss = { showCarpetDialog = false },
        )
    }
}

@Composable
private fun SettingRowClickable(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = iconTint.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), iconTint) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun SettingRowInfo(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    valueSupporting: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = iconTint.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), iconTint) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (valueSupporting != null) {
                Text(valueSupporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FirmwareUpdateRow(state: FirmwareUpdateState, onCheck: () -> Unit) {
    val checking = state is FirmwareUpdateState.Checking
    val value: String
    val supporting: String?
    val tint: Color
    when (state) {
        FirmwareUpdateState.Idle -> {
            value = "Check for updates"
            supporting = "Ask the cloud whether newer firmware is available."
            tint = MaterialTheme.colorScheme.primary
        }
        FirmwareUpdateState.Checking -> {
            value = "Checking…"
            supporting = null
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
        is FirmwareUpdateState.UpToDate -> {
            value = "Up to date"
            supporting = state.current?.let { "Installed: $it" }
            tint = AppColors.Good
        }
        is FirmwareUpdateState.Available -> {
            value = if (state.force) "Update required" else "Update available"
            supporting = buildString {
                append(state.current ?: "?"); append("  →  "); append(state.latest ?: "?")
                state.notes?.takeIf { it.isNotBlank() }?.let { append("\n"); append(it) }
            }
            tint = MaterialTheme.colorScheme.primary
        }
        is FirmwareUpdateState.Unavailable -> {
            value = "Check unavailable"
            supporting = state.reason
            tint = AppColors.Warn
        }
    }
    Surface(onClick = onCheck, enabled = !checking, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.SystemUpdate, null, Modifier.size(22.dp), tint) }
            }
            Column(Modifier.weight(1f)) {
                Text("Firmware updates", style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun SettingRowSwitch(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = iconTint.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), iconTint) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoteControlScreen(
    onMove: (velocity: Double, omega: Double) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Tap to move the robot",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(48.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RcButton(Icons.Default.KeyboardArrowUp, "Forward") { onMove(0.3, 0.0) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RcButton(Icons.Default.KeyboardArrowLeft, "Turn left") { onMove(0.0, 0.3) }
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Default.Stop, "Stop", Modifier.size(32.dp))
                    }
                    RcButton(Icons.Default.KeyboardArrowRight, "Turn right") { onMove(0.0, -0.3) }
                }
                RcButton(Icons.Default.KeyboardArrowDown, "Backward") { onMove(-0.3, 0.0) }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Each tap moves the robot for 1.5 seconds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RcButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
    ) {
        Icon(icon, contentDescription, Modifier.size(32.dp))
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinGoScreen(
    parsedMap: ParsedMap?,
    onGoto: (x: Int, y: Int) -> Unit,
    onBack: () -> Unit,
) {
    var tapPoint by remember { mutableStateOf<Offset?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var sent by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pin & Go") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    if (tapPoint != null && parsedMap != null && !sent) {
                        TextButton(onClick = {
                            val tp = tapPoint ?: return@TextButton
                            val pm = parsedMap ?: return@TextButton
                            if (imageSize.width > 0 && imageSize.height > 0) {
                                val mapX = (tp.x / imageSize.width * pm.width).toInt()
                                val mapY = (tp.y / imageSize.height * pm.height).toInt()
                                onGoto(mapX, mapY)
                                sent = true
                            }
                        }) { Text("Go") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (parsedMap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Map, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Map not loaded", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Go back to the main screen and wait for the map to load",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
            } else {
                val bitmap = rememberMapBitmap(parsedMap)
                val aspectRatio = parsedMap.width.toFloat() / parsedMap.height.toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                            .onSizeChanged { imageSize = it }
                            .pointerInput(parsedMap) {
                                detectTapGestures { offset ->
                                    tapPoint = offset
                                    sent = false
                                }
                            },
                    ) {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.None,
                        )
                        tapPoint?.let { tp ->
                            drawCircle(color = AppColors.AccentRed.copy(alpha = 0.85f), radius = 22f, center = tp)
                            drawCircle(color = Color.White, radius = 9f, center = tp)
                        }
                    }
                }
                Text(
                    text = when {
                        sent -> "Command sent! Robot is heading there."
                        tapPoint != null -> "Target set — tap Go to send the robot there."
                        else -> "Tap on the map to set a destination."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}


private fun oneDecimal(value: Float): String {
    val scaled = (value * 10f).roundToInt()
    return "${scaled / 10}.${(scaled % 10).absoluteValue}"
}

