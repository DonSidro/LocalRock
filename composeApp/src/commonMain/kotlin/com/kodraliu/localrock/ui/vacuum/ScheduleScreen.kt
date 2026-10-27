package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.kodraliu.localrock.ui.rememberUse24HourClock
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.TimerKind
import com.kodraliu.localrock.shared.vacuum.TimerSettings
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.kind
import com.kodraliu.localrock.shared.vacuum.summary
import com.kodraliu.localrock.shared.vacuum.timerSettingsFor
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val timers by repository.timers.collectAsState()
    val rooms by repository.rooms.collectAsState()
    val maps by repository.floorMaps.collectAsState()
    val map by repository.parsedMap.collectAsState()
    val currentFlag by repository.currentFloorFlag.collectAsState()
    val mapName = maps.firstOrNull { it.mapFlag == currentFlag }?.name ?: "Current map"
    LaunchedEffect(Unit) {
        try { repository.fetchFloorMaps() }
        catch (e: CancellationException) { currentCoroutineContext().ensureActive() }
        catch (_: Exception) { /* Room editing remains unavailable until a map is loaded. */ }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CleanTimer?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(refresh) {
        loading = true
        loadError = null
        try { repository.fetchTimers() }
        catch (e: CancellationException) { currentCoroutineContext().ensureActive(); loadError = "Schedule request timed out. Retry to refresh." }
        catch (e: Exception) { loadError = e.message ?: "Could not load schedules" }
        finally { loading = false }
    }

    // A refused save is shown, and the list is read back so it matches what the robot really holds.
    fun saveTimer(action: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                runCatching { repository.fetchTimers() }
                snackbarHostState.showSnackbar(e.message ?: "The schedule could not be saved")
            } finally { saving = false }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Schedules") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(enabled = !saving && !loading, onClick = { refresh++ }) { Icon(Icons.Default.Refresh, "Refresh schedules") }
                },
            )
        },
        floatingActionButton = {
            if (!saving && !loading && loadError == null) FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add schedule")
            }
        },
    ) { padding ->
        if (loading || loadError != null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (loading) "Loading schedules…" else loadError.orEmpty())
                if (!loading) TextButton(onClick = { refresh++ }) { Text("Retry") }
            }
        } else if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No schedules yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to schedule a recurring clean",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(timers.sortedWith(compareBy<CleanTimer> { it.hour }.thenBy { it.minute }.thenBy { it.id }), key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        busy = saving,
                        rooms = rooms,
                        currentFlag = currentFlag,
                        onEdit = { if (timer.editable) editing = timer },
                        onToggle = { saveTimer { repository.toggleTimer(timer) } },
                        onDelete = { saveTimer { repository.deleteTimer(timer) } },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        TimerDialog(
            initial = null,
            supportsSmartPlan = viewModel.supportsSmartPlan,
            supportsFastRoute = viewModel.supportsFastRoute,
            supportsWaterSlide = viewModel.supportsWaterSlide,
            rooms = rooms, map = map, mapName = mapName, currentFlag = currentFlag,
            onSave = { hour, minute, weekdays, settings, once, _, _, _ ->
                showAddDialog = false
                saveTimer { repository.createTimer(hour, minute, weekdays, settings, once) }
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editing?.let { timer ->
        TimerDialog(
            initial = timer,
            supportsSmartPlan = viewModel.supportsSmartPlan,
            supportsFastRoute = viewModel.supportsFastRoute,
            supportsWaterSlide = viewModel.supportsWaterSlide,
            rooms = rooms, map = map, mapName = mapName, currentFlag = currentFlag,
            onSave = { hour, minute, weekdays, settings, once, settingsChanged, timingChanged, enabled ->
                editing = null
                saveTimer { repository.updateTimer(timer, hour, minute, weekdays, settings, once, settingsChanged, timingChanged, enabled) }
            },
            readNativeState = { repository.nativeScheduleState(timer) },
            onDelete = { editing = null; saveTimer { repository.deleteTimer(timer) } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimerRow(timer: CleanTimer, busy: Boolean, rooms: List<ParsedMapRoom>, currentFlag: Int,
    onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, enabled = !busy && timer.editable,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (timer.editable) formatClock(timer.hour, timer.minute, rememberUse24HourClock()) else timer.serverJob?.cron.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(formatRepeat(timer), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Switch(checked = timer.enabled, enabled = !busy, onCheckedChange = { onToggle() })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val segments = timer.settings.segments.orEmpty()
                val currentMap = timer.settings.mapIndex == null || timer.settings.mapIndex == -1 || timer.settings.mapIndex == currentFlag
                val roomNames = segments.split(',', ';').mapNotNull { it.trim().toIntOrNull() }.map { id ->
                    if (currentMap) rooms.firstOrNull { it.id == id }?.name ?: "Room $id" else "Room $id"
                }
                Text(if (segments.isBlank() || segments == "0") "Full" else roomNames.joinToString(", ").ifBlank { "Saved rooms" },
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(timer.settings.kind.label(), style = MaterialTheme.typography.bodySmall)
            }
            if (!timer.editable) {
                Text("Unsupported format · read-only editor", style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !busy, onClick = onDelete) { Text("Delete schedule") }
            }
        }
    }
}

private fun TimerKind.label(): String = when (this) {
    TimerKind.CURRENT -> "Saved settings"
    TimerKind.SMART_PLAN -> "SmartPlan"
    TimerKind.CUSTOM -> "Custom"
    TimerKind.VACUUM -> "Vacuum"
    TimerKind.VAC_AND_MOP -> "Vac & Mop"
    TimerKind.MOP -> "Mop"
}

/** Adds a schedule, or edits [initial]. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TimerDialog(
    initial: CleanTimer?,
    supportsSmartPlan: Boolean,
    supportsFastRoute: Boolean,
    supportsWaterSlide: Boolean,
    rooms: List<ParsedMapRoom>, map: ParsedMap?, mapName: String, currentFlag: Int,
    onSave: (hour: Int, minute: Int, weekdays: Set<Int>, settings: TimerSettings, once: Boolean, settingsChanged: Boolean, timingChanged: Boolean, enabled: Boolean) -> Unit,
    readNativeState: (suspend () -> String)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val start = initial?.settings ?: TimerSettings()
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var touchedSettings by remember { mutableStateOf(emptySet<String>()) }
    val settingsChanged = touchedSettings.isNotEmpty()
    var moreDetails by remember { mutableStateOf(false) }
    var nativeState by remember { mutableStateOf("Not checked") }
    val detailScope = rememberCoroutineScope()
    var hour by remember { mutableIntStateOf(initial?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initial?.minute ?: 0) }
    // Once is the official app's default for a new schedule.
    var repeatChoice by remember { mutableStateOf(initial?.let { repeatChoiceOf(it) } ?: RepeatChoice.ONCE) }
    var weekdays by remember { mutableStateOf(initial?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(1, 2, 3, 4, 5)) }
    // Like the official app, every schedule picks a mode, opening on Vac & Mop. An old schedule that
    // carries no settings (it ran with whatever the robot had) opens on it too and is saved with them.
    var kind by remember { mutableStateOf(start.kind.takeIf { it != TimerKind.CURRENT } ?: TimerKind.VAC_AND_MOP) }
    // Off (no suction / no water) is what Vacuum-only and Mop-only mean, not a level of its own, and
    // the smart codes are what SmartPlan means, so none of them is a choice in the rows below.
    var fanPower by remember {
        mutableStateOf(start.fanPower?.takeIf { it != VacuumFanPower.OFF && it != VacuumFanPower.SMART } ?: VacuumFanPower.BALANCED)
    }
    var waterBoxMode by remember {
        // Kept as LOW/MEDIUM/HIGH whichever codes the robot uses; the slide codes are applied on save.
        mutableStateOf(
            WaterBoxMode.normalize(start.waterBoxMode)?.takeIf { it != WaterBoxMode.OFF && it != WaterBoxMode.SMART }
                ?: WaterBoxMode.MEDIUM,
        )
    }
    var mopMode by remember { mutableStateOf(start.mopMode?.takeIf { it != MopRoute.SMART } ?: MopRoute.STANDARD) }
    var repeat by remember { mutableIntStateOf(start.repeat ?: 1) }

    var sheet by remember { mutableStateOf<String?>(null) }
    var wholeHome by remember { mutableStateOf(start.segments.isNullOrBlank() || start.segments == "0") }
    var selected by remember { mutableStateOf(start.segments.orEmpty().split(',', ';').mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 } }) }
    var roomsChanged by remember { mutableStateOf(false) }
    var confirmedMap by remember { mutableStateOf<Int?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun save() {
        val selectedSettings = timerSettingsFor(
            kind = kind,
            fanPower = fanPower,
            waterBoxMode = if (supportsWaterSlide) WaterBoxMode.forSlide(waterBoxMode) else waterBoxMode,
            mopMode = mopMode,
            repeat = repeat,
        )
        val cleaning = if (initial == null) selectedSettings else start.copy(
            fanPower = if ("fan" in touchedSettings) selectedSettings.fanPower else start.fanPower,
            waterBoxMode = if ("water" in touchedSettings) selectedSettings.waterBoxMode else start.waterBoxMode,
            mopMode = if ("route" in touchedSettings) selectedSettings.mopMode else start.mopMode,
            repeat = if ("repeat" in touchedSettings) selectedSettings.repeat else start.repeat,
        )
        if (roomsChanged && !wholeHome && (confirmedMap != currentFlag || selected.isEmpty() || selected.any { id -> rooms.none { it.id == id } })) {
            localError = "Choose rooms from the intended loaded map before saving."
            return
        }
        val settings = if (roomsChanged) cleaning.copy(segments = if (wholeHome) "0" else selected.joinToString(",")) else cleaning
        val days = when (repeatChoice) {
            RepeatChoice.ONCE, RepeatChoice.EVERY_DAY -> emptySet()
            RepeatChoice.WEEKDAYS -> WEEKDAYS
            RepeatChoice.WEEKENDS -> WEEKENDS
            RepeatChoice.CUSTOM -> weekdays
        }
        val timingChanged = initial == null || hour != initial.hour || minute != initial.minute ||
            repeatChoice != repeatChoiceOf(initial) ||
            (repeatChoice == RepeatChoice.CUSTOM && days != initial.weekdays)
        onSave(hour, minute, days, if (initial != null && !settingsChanged && !roomsChanged) start else settings,
            repeatChoice == RepeatChoice.ONCE, settingsChanged || roomsChanged, timingChanged, enabled)

    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (initial == null) "New schedule" else "Scheduling") }, navigationIcon = {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel editing") }
            }, actions = {
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete schedule") }
                IconButton(enabled = repeatChoice != RepeatChoice.CUSTOM || weekdays.isNotEmpty(), onClick = { save() }) { Icon(Icons.Default.Check, "Save schedule") }
            })
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Card(shape = RoundedCornerShape(20.dp)) {
                    ScheduleRow("Start", formatClock(hour, minute, rememberUse24HourClock())) { sheet = "time" }
                    HorizontalDivider()
                    ScheduleRow("Repeat", if (repeatChoice == RepeatChoice.ONCE && initial?.date != null && hour == initial.hour && minute == initial.minute) formatRepeat(initial) else repeatChoice.label) { sheet = "repeat" }
                }
                Text("Task", style = MaterialTheme.typography.labelLarge)
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Full", "Rooms").forEachIndexed { index, label ->
                                SegmentedButton(selected = wholeHome == (index == 0), shape = SegmentedButtonDefaults.itemShape(index, 2), onClick = {
                                    if (index == 0) { wholeHome = true; roomsChanged = true } else sheet = "rooms"
                                }) { Text(label) }
                            }
                        }
                        ScheduleRow("Cleaning Mode", if (initial != null && !settingsChanged) start.kind.label() else kind.label()) { sheet = "mode" }
                        ScheduleRow("Map", mapName) { sheet = "rooms" }
                        if (map != null) VacuumMapImage(map, if (wholeHome) emptyList() else selected,
                            onRoomTap = { sheet = "rooms" }, modifier = Modifier.fillMaxWidth().height(220.dp))
                        Text(if (wholeHome) "Entire map" else selected.joinToString(", ") { id -> rooms.firstOrNull { it.id == id }?.name ?: "Room $id" },
                            modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodyMedium)
                        Text("Preview: currently loaded map", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (initial?.serverJob != null) Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it })
                    }
                }
                if (initial?.serverJob != null) {
                    TextButton(onClick = { moreDetails = !moreDetails }) { Text("More details") }
                    if (moreDetails) {
                        Text("Enabled updates the server job and verifies its linked native timer when present.", style = MaterialTheme.typography.bodySmall)
                        Text("Job: ${initial.id}", style = MaterialTheme.typography.bodySmall)
                        initial.settings.mapIndex?.let { Text("Saved map reference: $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Changing rooms preserves the saved map reference. Confirm the intended map is loaded before replacing rooms.", style = MaterialTheme.typography.bodySmall)
                        Text("Timezone: ${initial.serverJob.timezone.ifBlank { "Server default" }}", style = MaterialTheme.typography.bodySmall)
                        Text("Native timer: $nativeState", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            detailScope.launch {
                                nativeState = "Checking…"
                                try { nativeState = readNativeState?.invoke() ?: "Not checked" }
                                catch (e: CancellationException) { currentCoroutineContext().ensureActive(); nativeState = "Unknown — request timed out" }
                                catch (_: Exception) { nativeState = "Unknown — could not read timer" }
                            }
                        }) { Text("Check native timer") }
                    }
                }

            }
        }
        when (sheet) {
            "time" -> {
                val time = rememberTimePickerState(hour, minute, is24Hour = rememberUse24HourClock())
                AlertDialog(onDismissRequest = { sheet = null }, title = { Text("Start time") }, text = { TimeInput(time) },
                    confirmButton = { TextButton(onClick = { hour = time.hour; minute = time.minute; sheet = null }) { Text("Done") } },
                    dismissButton = { TextButton(onClick = { sheet = null }) { Text("Cancel") } })
            }
            "repeat" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChipRow(
                        label = "Repeat",
                        options = RepeatChoice.entries.map { it to it.label },
                        selected = repeatChoice,
                        onSelect = { repeatChoice = it },
                    )
                    if (repeatChoice == RepeatChoice.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val dayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                        dayLabels.forEachIndexed { index, label ->
                            val selected = index in weekdays
                            Surface(
                                onClick = { weekdays = if (selected) weekdays - index else weekdays + index },
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }

                    Button(enabled = repeatChoice != RepeatChoice.CUSTOM || weekdays.isNotEmpty(), onClick = { sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
            "mode" -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChipRow(
                        label = "Cleaning",
                        options = buildList {
                            if (supportsSmartPlan || kind == TimerKind.SMART_PLAN) add(TimerKind.SMART_PLAN to "SmartPlan")
                            add(TimerKind.CUSTOM to "Custom")
                            add(TimerKind.VAC_AND_MOP to "Vacuum and mop")
                            add(TimerKind.MOP to "Mop")
                            add(TimerKind.VACUUM to "Vacuum")
                        },
                        selected = kind,
                        onSelect = { kind = it; touchedSettings = touchedSettings + setOf("fan", "water", "route") },
                    )
                    if (kind == TimerKind.VACUUM || kind == TimerKind.VAC_AND_MOP) {
                        ChipRow(
                            label = "Suction",
                            options = listOf(
                                VacuumFanPower.QUIET to "Quiet",
                                VacuumFanPower.BALANCED to "Balanced",
                                VacuumFanPower.TURBO to "Turbo",
                                VacuumFanPower.MAX to "Max",
                            ),
                            selected = fanPower,
                            onSelect = { fanPower = it; touchedSettings = touchedSettings + "fan" },
                        )
                    }
                    if (kind == TimerKind.VAC_AND_MOP || kind == TimerKind.MOP) {
                        ChipRow(
                            label = "Water",
                            options = listOf(
                                WaterBoxMode.LOW to "Low",
                                WaterBoxMode.MEDIUM to "Medium",
                                WaterBoxMode.HIGH to "High",
                            ),
                            selected = waterBoxMode,
                            onSelect = { waterBoxMode = it; touchedSettings = touchedSettings + "water" },
                        )
                        ChipRow(
                            label = "Mop route",
                            // A schedule can only use these two routes: a Qrevo Pro that accepts Deep and
                            // Deep+ as its route setting refuses them inside a start command with -10005
                            // "Param error", and the official app's schedule screen offers only these. A
                            // route an existing schedule already carries stays visible so it is not dropped.
                            options = buildList {
                                add(MopRoute.STANDARD to "Standard")
                                if (supportsFastRoute || mopMode == MopRoute.FAST) add(MopRoute.FAST to "Fast")
                                if (mopMode == MopRoute.DEEP) add(MopRoute.DEEP to "Deep")
                                if (mopMode == MopRoute.DEEP_PLUS) add(MopRoute.DEEP_PLUS to "Deep+")
                            },
                            selected = mopMode,
                            onSelect = { mopMode = it; touchedSettings = touchedSettings + "route" },
                        )
                    }
                    ChipRow(
                        label = "Passes",
                        options = listOf(1 to "1", 2 to "2", 3 to "3"),
                        selected = repeat,
                        onSelect = { repeat = it; touchedSettings = touchedSettings + "repeat" },
                    )

                    if (kind == TimerKind.CUSTOM) Text("Uses the per-room cleaning settings saved on the robot.")
                    Button(onClick = { sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
            "rooms" -> {
                var draftFull by remember { mutableStateOf(false) }
                var draftRooms by remember { mutableStateOf(selected) }
                var confirmed by remember(currentFlag) { mutableStateOf(initial == null || confirmedMap == currentFlag) }
                val unknown = draftRooms.filter { id -> rooms.none { it.id == id } }
                ModalBottomSheet(onDismissRequest = { sheet = null }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mapName, style = MaterialTheme.typography.titleLarge)
                        Text("Currently loaded map. Load another floor from Maps before selecting its rooms.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(draftFull, { draftFull = it }); Text("Full — entire map") }
                        if (!draftFull) {
                            if (initial != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(confirmed, { confirmed = it }); Text("This is the map for this schedule")
                            }
                            if (map != null) VacuumMapImage(map, draftRooms, onRoomTap = { id ->
                                if (rooms.any { it.id == id }) draftRooms = if (id in draftRooms) draftRooms - id else draftRooms + id
                            }, modifier = Modifier.fillMaxWidth().height(220.dp))
                            if (rooms.isEmpty()) Text("No rooms loaded. Open the vacuum map, then return here.")
                            rooms.forEach { room ->
                                Row(Modifier.fillMaxWidth().clickable { draftRooms = if (room.id in draftRooms) draftRooms - room.id else draftRooms + room.id }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(room.id in draftRooms, { checked -> draftRooms = if (checked) (draftRooms + room.id).distinct() else draftRooms - room.id })
                                    Text(room.name, Modifier.weight(1f))
                                }
                            }
                            if (unknown.isNotEmpty()) {
                                Text("Some saved rooms are unavailable on this map.")
                                TextButton(enabled = confirmed, onClick = { draftRooms = draftRooms - unknown.toSet() }) { Text("Remove unavailable rooms") }
                            }
                        }
                        Button(enabled = draftFull || (confirmed && draftRooms.isNotEmpty() && unknown.isEmpty()), onClick = {
                            wholeHome = draftFull; selected = draftRooms; roomsChanged = true; confirmedMap = currentFlag; sheet = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A labelled group of single-choice chips that wraps onto more lines, so the chosen one is always in view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}

@Composable
internal fun NumberStepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepButton("+") { onChange(steppedWithWrap(value, step, min, max)) }
        Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        StepButton("−") { onChange(steppedWithWrap(value, -step, min, max)) }
    }
}

/**
 * [value] moved by [delta] within [min]..[max], wrapping around the ends of the range like a clock.
 * Minutes in steps of 15 go 45, 30, 15, 0 and back to 45, not 0 then 59, 44, 29, 14.
 */
internal fun steppedWithWrap(value: Int, delta: Int, min: Int, max: Int): Int {
    val size = max - min + 1
    return ((value - min + delta) % size + size) % size + min
}

private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
private val WEEKENDS = setOf(0, 6)

/** The official app's Repeat choices; Custom shows the day buttons. */
private enum class RepeatChoice(val label: String) {
    ONCE("Once"),
    EVERY_DAY("Every day"),
    WEEKDAYS("Weekdays"),
    WEEKENDS("Weekends"),
    CUSTOM("Custom"),
}

private fun repeatChoiceOf(timer: CleanTimer): RepeatChoice = when {
    timer.date != null || timer.serverJob?.repeated == false -> RepeatChoice.ONCE
    timer.weekdays.isEmpty() || timer.weekdays.size == 7 -> RepeatChoice.EVERY_DAY
    timer.weekdays == WEEKDAYS -> RepeatChoice.WEEKDAYS
    timer.weekdays == WEEKENDS -> RepeatChoice.WEEKENDS
    else -> RepeatChoice.CUSTOM
}

private fun formatRepeat(timer: CleanTimer): String {
    timer.date?.let { date ->
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "Once, ${months.getOrElse(date.month - 1) { "${date.month}" }} ${date.day}"
    }
    return when (val choice = repeatChoiceOf(timer)) {
        RepeatChoice.CUSTOM -> {
            val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            timer.weekdays.sorted().joinToString(", ") { names.getOrElse(it) { "$it" } }
        }
        else -> choice.label
    }
}
