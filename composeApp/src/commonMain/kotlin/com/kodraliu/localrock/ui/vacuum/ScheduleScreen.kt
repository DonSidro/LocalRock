package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.clickable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    val timers by repository.timers.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CleanTimer?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { runCatching { repository.fetchTimers() } }

    // A refused save is shown, and the list is read back so it matches what the robot really holds.
    fun saveTimer(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                runCatching { repository.fetchTimers() }
                snackbarHostState.showSnackbar(e.message ?: "The schedule could not be saved")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add schedule")
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
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
            LazyColumn(contentPadding = padding) {
                items(timers, key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        onEdit = { editing = timer },
                        onToggle = { saveTimer { repository.toggleTimer(timer) } },
                        onDelete = { saveTimer { repository.deleteTimer(timer) } },
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp))
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
            onSave = { hour, minute, weekdays, settings, once ->
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
            onSave = { hour, minute, weekdays, settings, once ->
                editing = null
                saveTimer { repository.updateTimer(timer, hour, minute, weekdays, settings, once) }
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimerRow(timer: CleanTimer, onEdit: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (timer.enabled) 1f else 0.4f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Schedule, null, Modifier.size(22.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatTime(timer.hour, timer.minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (timer.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatRepeat(timer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            timer.settings.summary()?.let { summary ->
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = timer.enabled, onCheckedChange = { onToggle() })
    }
}

/** Adds a schedule, or edits [initial]. */
@Composable
private fun TimerDialog(
    initial: CleanTimer?,
    supportsSmartPlan: Boolean,
    supportsFastRoute: Boolean,
    supportsWaterSlide: Boolean,
    onSave: (hour: Int, minute: Int, weekdays: Set<Int>, settings: TimerSettings, once: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = initial?.settings ?: TimerSettings()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New schedule" else "Edit schedule") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberStepper("Hour", hour, 0, 23) { hour = it }
                    Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    NumberStepper("Min", minute, 0, 59, step = 15) { minute = it }
                }
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

                ChipRow(
                    label = "Cleaning",
                    options = buildList {
                        if (supportsSmartPlan || kind == TimerKind.SMART_PLAN) add(TimerKind.SMART_PLAN to "SmartPlan")
                        add(TimerKind.VAC_AND_MOP to "Vacuum and mop")
                        add(TimerKind.MOP to "Mop")
                        add(TimerKind.VACUUM to "Vacuum")
                    },
                    selected = kind,
                    onSelect = { kind = it },
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
                        onSelect = { fanPower = it },
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
                        onSelect = { waterBoxMode = it },
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
                        onSelect = { mopMode = it },
                    )
                }
                ChipRow(
                    label = "Passes",
                    options = listOf(1 to "1", 2 to "2", 3 to "3"),
                    selected = repeat,
                    onSelect = { repeat = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                // Custom with no day picked would never run.
                enabled = repeatChoice != RepeatChoice.CUSTOM || weekdays.isNotEmpty(),
                onClick = {
                    val settings = timerSettingsFor(
                        kind = kind,
                        fanPower = fanPower,
                        waterBoxMode = if (supportsWaterSlide) WaterBoxMode.forSlide(waterBoxMode) else waterBoxMode,
                        mopMode = mopMode,
                        repeat = repeat,
                    )
                    val days = when (repeatChoice) {
                        RepeatChoice.ONCE, RepeatChoice.EVERY_DAY -> emptySet()
                        RepeatChoice.WEEKDAYS -> WEEKDAYS
                        RepeatChoice.WEEKENDS -> WEEKENDS
                        RepeatChoice.CUSTOM -> weekdays
                    }
                    onSave(hour, minute, days, settings, repeatChoice == RepeatChoice.ONCE)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun NumberStepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onChange(if (value + step > max) min else value + step) }, modifier = Modifier.size(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+")
        }
        Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange(if (value - step < min) max else value - step) }, modifier = Modifier.size(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−")
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "${h.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
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
    timer.date != null -> RepeatChoice.ONCE
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
