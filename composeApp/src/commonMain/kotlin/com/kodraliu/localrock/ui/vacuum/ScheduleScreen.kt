package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.*
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.schedule.*
import com.kodraliu.localrock.shared.vacuum.*
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    var legacy by remember { mutableStateOf(false) }
    if (legacy) {
        LegacyScheduleScreen(viewModel) { legacy = false }
        return
    }
    val api = viewModel.scheduleApi
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val enablement = remember(api, repository) {
        ScheduleEnablement(
            readJobs = { api.list(repository.duid) },
            writeJob = { expected, patch -> api.update(repository.duid, expected, patch) },
            readTimers = { repository.session.sendCommand("get_server_timer").requireOk("get_server_timer").result },
            writeTimer = { name, enabled ->
                repository.session.sendCommand("upd_server_timer", nativeTimerUpdateParams(name, enabled)).requireOk("upd_server_timer")
            },
        )
    }
    var jobs by remember { mutableStateOf<List<ServerSchedule>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var nativeStates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editing by remember { mutableStateOf<ServerSchedule?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ServerSchedule?>(null) }
    val rooms by repository.rooms.collectAsState()
    val maps by repository.floorMaps.collectAsState()
    val map by repository.parsedMap.collectAsState()
    val currentFlag by repository.currentFloorFlag.collectAsState()
    val mapName = maps.firstOrNull { it.mapFlag == currentFlag }?.name
        ?: maps.singleOrNull()?.name ?: "Current map"
    var help by remember { mutableStateOf(false) }

    suspend fun refresh() {
        jobs = api.list(repository.duid)
        loaded = true
        try { repository.fetchFloorMaps() }
        catch (e: CancellationException) { currentCoroutineContext().ensureActive() }
        catch (_: Exception) { /* Schedule editing remains available offline. */ }
        nativeStates = emptyMap()
        if (jobs.any { it.nativeName != null }) {
            try {
                val reply = repository.session.sendCommand("get_server_timer").requireOk("get_server_timer")
                nativeStates = jobs.mapNotNull { job -> job.nativeName?.let { job.id to nativeTimerState(reply.result, it) } }.toMap()
            } catch (e: CancellationException) { currentCoroutineContext().ensureActive() }
            catch (_: Exception) { /* Timer diagnostics remain unknown in More details. */ }
        }
    }
    suspend fun refreshAfterSave() {
        try { refresh() }
        catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            notice = "Save confirmed. The list could not be refreshed; tap Refresh."
        }
        catch (_: Exception) { notice = "Save confirmed. The list could not be refreshed; tap Refresh." }
    }
    fun action(block: suspend () -> Unit) {
        if (busy || viewModel.scheduleDemoMode) return
        busy = true; error = null; notice = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                nativeStates = emptyMap() // A partial operation must not leave stale diagnostics visible.
                error = if (e is ScheduleException || e is IllegalArgumentException) e.message
                    else "Request could not be confirmed. Refresh before retrying; a write may already have reached the server."
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { action { refresh() } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Schedules") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            IconButton(onClick = { help = true }) { Icon(Icons.Default.Info, "Schedule information") }
            IconButton(enabled = !busy && !viewModel.scheduleDemoMode, onClick = { action { refresh() } }) {
                Icon(Icons.Default.Refresh, "Refresh schedules")
            }
            IconButton(enabled = loaded && !busy && !viewModel.scheduleDemoMode,
                onClick = { creating = true; error = null }) { Icon(Icons.Default.Add, "Add schedule") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (viewModel.scheduleDemoMode) Text("Server schedules are unavailable in demo mode.")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (loaded && jobs.isEmpty()) Text("No schedules yet. Tap + to add one.")
            jobs.sortedWith(compareBy<ServerSchedule> { it.weekly?.hour ?: 24 }
                .thenBy { it.weekly?.minute ?: 60 }.thenBy { it.id }).forEach { job ->
                Card(onClick = { editing = job; error = null }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(job.weekly?.let { formatScheduleTime(it.hour, it.minute) } ?: "Advanced",
                                style = MaterialTheme.typography.headlineMedium)
                            Text(job.weekly?.let { scheduleRepeatLabel(job.repeated, it.days) } ?: "Custom repeat",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Switch(checked = job.enabled, enabled = !busy && (job.editable || job.repeated || job.enabled),
                                onCheckedChange = { enabled -> action {
                                    val saved = enablement.save(job, buildJsonObject { put("enabled", enabled) }, synchronizeEnabled = true)
                                    jobs = jobs.map { if (it.id == saved.id) saved else it }
                                    refreshAfterSave()
                                } })
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val segments = job.settingsText("segments")
                            Text(if (segments == "0") "Full" else "Rooms",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(ScheduleCleanMode.from(job.settings)?.label ?: "Saved settings",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text("About schedules") },
        text = { Text("The local server must be running at the start time. Imported schedules also need their robot timer switched on. Once runs at the next matching time, then turns off. New schedules are saved disabled until you enable them. Room cleans use the loaded map; automatic floor switching is not supported here.") },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Done") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { help = false; legacy = true }) { Text("Legacy timers") } })
    if (creating || editing != null) {
        ScheduleEditor(editing, viewModel.scheduleTimezone, rooms, map, mapName, currentFlag, busy, error,
            nativeState = editing?.let { nativeStates[it.id] } ?: "Not checked",
            onDelete = { editing?.let { deleting = it; editing = null } },
            onDismiss = { if (!busy) { creating = false; editing = null; error = null } },
            onSave = { body, synchronizeEnabled ->
                val existing = editing
                action {
                    if (existing == null) api.create(repository.duid, body)
                    else {
                        val saved = enablement.save(existing, body, synchronizeEnabled)
                        jobs = jobs.map { if (it.id == saved.id) saved else it }
                    }
                    creating = false; editing = null
                    notice = "Schedule saved."
                    refreshAfterSave()
                }
            })
    }
    deleting?.let { job ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Delete schedule?") },
            text = { Column {
                Text("Remove this job from the local server? Its robot timer, if present, will be left untouched. Re-importing cloud data can bring the job back.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(enabled = !busy, onClick = { action {
                api.delete(repository.duid, job); deleting = null; refresh()
            } }) { Text("Delete") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") } })
    }
}

private fun formatScheduleTime(hour: Int, minute: Int) =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(job: ServerSchedule?, defaultTimezone: String, rooms: List<ParsedMapRoom>,
    map: ParsedMap?, mapName: String, currentFlag: Int, busy: Boolean, error: String?, nativeState: String,
    onDelete: () -> Unit, onDismiss: () -> Unit, onSave: (JsonObject, Boolean) -> Unit) {
    val initial = job?.weekly ?: WeeklyTime(8, 0, (1..5).toSet())
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var days by remember { mutableStateOf(initial.days) }
    var repeated by remember { mutableStateOf(job?.repeated ?: true) }
    var enabled by remember { mutableStateOf(job?.enabled ?: false) }
    var enablementRequested by remember { mutableStateOf(false) }
    var zone by remember { mutableStateOf(job?.timezone ?: defaultTimezone) }
    var wholeHome by remember { mutableStateOf(job == null || job.settingsText("segments") == "0") }
    var selected by remember { mutableStateOf(job?.settingsText("segments").orEmpty().split(',', ';')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } }) }
    var roomsChanged by remember { mutableStateOf(false) }
    var confirmedMap by remember { mutableStateOf<Int?>(null) }
    var changes by remember { mutableStateOf<Map<String, JsonElement>>(emptyMap()) }
    var localError by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf(false) }
    val creationId = remember { Random.nextLong(1, 1L shl 53) }
    val editable = job == null || job.editable
    val effectiveSettings = JsonObject(job?.settings.orEmpty() + changes)
    val mode = ScheduleCleanMode.from(effectiveSettings)

    fun save() {
        try {
            val time = WeeklyTime(hour, minute, days)
            time.cron()
            val settings = changes.toMutableMap()
            if (roomsChanged) {
                require(wholeHome || (confirmedMap == currentFlag && selected.isNotEmpty() &&
                    selected.all { id -> rooms.any { it.id == id } })) { "Select rooms from the intended loaded map." }
                settings["segments"] = JsonPrimitive(if (wholeHome) "0" else selected.joinToString(","))
            }
            val payload = job?.update(time, zone, enabled, settings, repeated)
                ?: JsonObject(ServerSchedule.create(time, zone, settings, repeated).toMutableMap().apply {
                    put("id", JsonPrimitive(creationId)); put("enabled", JsonPrimitive(enabled))
                })
            localError = null
            // A time/settings edit must not synchronize or overwrite enablement.
            val patch = if (job != null && !enablementRequested) JsonObject(payload - "enabled") else payload
            onSave(patch, enablementRequested)
        } catch (e: IllegalArgumentException) { localError = e.message }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(if (job == null) "New schedule" else "Scheduling") }, navigationIcon = {
                IconButton(enabled = !busy, onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel editing") }
            }, actions = {
                if (job != null) IconButton(enabled = !busy, onClick = onDelete) { Icon(Icons.Default.Delete, "Delete schedule") }
                IconButton(enabled = !busy && editable, onClick = { save() }) { Icon(Icons.Default.Check, "Save schedule") }
            })
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!editable) Text("This advanced schedule is read-only. Its existing settings are preserved.")
                Card(shape = RoundedCornerShape(20.dp)) {
                    ScheduleRow("Start", if (editable) formatScheduleTime(hour, minute) else job?.cron.orEmpty(), !busy && editable) { sheet = "time" }
                    HorizontalDivider()
                    ScheduleRow("Repeat", if (editable) scheduleRepeatLabel(repeated, days) else "Advanced", !busy && editable) { sheet = "repeat" }
                }
                Text("Task", style = MaterialTheme.typography.labelLarge)
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Full", "Rooms").forEachIndexed { index, label ->
                                SegmentedButton(selected = wholeHome == (index == 0), enabled = !busy && editable,
                                    shape = SegmentedButtonDefaults.itemShape(index, 2), onClick = {
                                        if (index == 0) { wholeHome = true; roomsChanged = true }
                                        else { sheet = "rooms" }
                                    }) { Text(label) }
                            }
                        }
                        ScheduleRow("Cleaning Mode", mode?.label ?: "Saved settings", !busy && editable) { sheet = "mode" }
                        ScheduleRow("Map", mapName, !busy && editable) { sheet = "rooms" }
                        if (map != null) {
                            VacuumMapImage(map, if (wholeHome) emptyList() else selected,
                                onRoomTap = { if (!busy && editable) sheet = "rooms" },
                                modifier = Modifier.fillMaxWidth().height(220.dp))
                        }
                        Text(if (wholeHome) "Entire map" else "${selected.size} rooms selected",
                            modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium)
                        if (!wholeHome && roomsChanged) Text(selected.joinToString(", ") { id ->
                            rooms.firstOrNull { it.id == id }?.name ?: "Room $id"
                        }, style = MaterialTheme.typography.bodySmall)
                        Text("Map preview: currently loaded map", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Card(shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Enabled", Modifier.weight(1f))
                        Switch(enabled, { enabled = it; enablementRequested = true }, enabled = !busy && editable)
                    }
                }
                if (!repeated) Text("Runs once at the next matching start time after you enable it, then turns off.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "More details") }
                if (details) {
                    if (job?.nativeName != null) {
                        Text("Robot timer (last check): $nativeState", style = MaterialTheme.typography.bodySmall)
                        Text("Enabled synchronizes this native timer and the server job, with read-back verification. Time and cleaning edits leave the timer state unchanged.",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !busy && editable, onClick = { enablementRequested = true }) {
                            Text("Synchronize enabled state")
                        }
                        if (enablementRequested) Text("The selected Enabled state will be synchronized when you save.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(zone, { zone = it }, label = { Text("Timezone") }, enabled = !busy && editable,
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Times follow this timezone, including daylight-saving changes.", style = MaterialTheme.typography.bodySmall)
                    job?.mapIndex?.let { Text("Saved map reference: $it", style = MaterialTheme.typography.bodySmall) }
                    job?.nextFire?.let { Text("Next run (UTC): $it", style = MaterialTheme.typography.bodySmall) }
                    Text("The map shown is the currently loaded map. Changing rooms does not change an imported schedule's map reference. Keep the intended map loaded for new room schedules.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        when (sheet) {
            "time" -> {
                val time = rememberTimePickerState(hour, minute, is24Hour = true)
                AlertDialog(onDismissRequest = { sheet = null }, title = { Text("Start time") },
                    text = { TimeInput(time) },
                    confirmButton = { TextButton(onClick = { hour = time.hour; minute = time.minute; sheet = null }) { Text("Done") } },
                    dismissButton = { TextButton(onClick = { sheet = null }) { Text("Cancel") } })
            }
            "repeat" -> {
                var preset by remember { mutableStateOf(ScheduleRepeat.from(repeated, days)) }
                var customDays by remember { mutableStateOf(days) }
                ModalBottomSheet(onDismissRequest = { sheet = null }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Repeat", style = MaterialTheme.typography.titleLarge)
                        ScheduleRepeat.entries.forEach { choice ->
                            Row(Modifier.fillMaxWidth().clickable { preset = choice }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(preset == choice, onClick = { preset = choice })
                                Text(choice.label)
                            }
                        }
                        if (preset == ScheduleRepeat.CUSTOM) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 3, 4, 5, 6, 0).forEach { day ->
                                    FilterChip(selected = day in customDays,
                                        onClick = { customDays = if (day in customDays) customDays - day else customDays + day },
                                        label = { Text(scheduleDayNames[day]) })
                                }
                            }
                            if (customDays.isEmpty()) Text("Choose at least one day.", color = MaterialTheme.colorScheme.error)
                        }
                        Button(enabled = preset != ScheduleRepeat.CUSTOM || customDays.isNotEmpty(),
                            onClick = {
                                repeated = preset != ScheduleRepeat.ONCE
                                days = preset.days ?: customDays
                                sheet = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
            "rooms" -> {
                // Draft selection is committed only with Done; dismissing cannot replace imported room IDs.
                var draftFull by remember { mutableStateOf(false) }
                var draftRooms by remember { mutableStateOf(selected) }
                var confirmed by remember(currentFlag) { mutableStateOf(job == null || confirmedMap == currentFlag) }
                val unknown = draftRooms.filter { id -> rooms.none { it.id == id } }
                ModalBottomSheet(onDismissRequest = { sheet = null }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mapName, style = MaterialTheme.typography.titleLarge)
                        Text("Currently loaded map. To use another floor, load it from Maps first.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(draftFull, { draftFull = it }); Text("Full — entire map")
                        }
                        if (!draftFull) {
                            if (job != null) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(confirmed, { confirmed = it })
                                Text("This is the map for this schedule", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (map != null) VacuumMapImage(map, draftRooms, onRoomTap = { id ->
                                if (rooms.any { it.id == id }) draftRooms = if (id in draftRooms) draftRooms - id else draftRooms + id
                            }, modifier = Modifier.fillMaxWidth().height(200.dp))
                            if (rooms.isEmpty()) Text("No rooms loaded. Open the vacuum map, then return here.")
                            rooms.forEach { room ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    draftRooms = if (room.id in draftRooms) draftRooms - room.id else draftRooms + room.id
                                }, verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(room.id in draftRooms, onCheckedChange = { checked ->
                                        draftRooms = if (checked) (draftRooms + room.id).distinct() else draftRooms - room.id
                                    })
                                    Text(room.name, Modifier.weight(1f))
                                }
                            }
                            if (unknown.isNotEmpty()) {
                                Text("Some saved rooms aren't on this map. Confirm the intended map before replacing them.", style = MaterialTheme.typography.bodySmall)
                                TextButton(enabled = confirmed, onClick = { draftRooms = draftRooms - unknown.toSet() }) { Text("Remove unavailable rooms") }
                            }
                        }
                        Button(enabled = draftFull || (confirmed && draftRooms.isNotEmpty() && unknown.isEmpty()),
                            onClick = {
                                wholeHome = draftFull; selected = draftRooms; roomsChanged = true
                                confirmedMap = currentFlag; sheet = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
            "mode" -> {
                var draft by remember { mutableStateOf(changes) }
                val draftMode = ScheduleCleanMode.from(JsonObject(job?.settings.orEmpty() + draft))
                ModalBottomSheet(onDismissRequest = { sheet = null }) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Cleaning Mode", style = MaterialTheme.typography.titleLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScheduleCleanMode.entries.forEach { choice ->
                                FilterChip(selected = draftMode == choice, onClick = { draft = draft + choice.settings() }, label = { Text(choice.label) })
                            }
                        }
                        if (draftMode == ScheduleCleanMode.CUSTOM) {
                            Text("Uses the per-room cleaning settings already saved on the robot.")
                        } else {
                            if (draftMode != ScheduleCleanMode.MOP) ScheduleChoice("Suction", "fan_power", job, draft,
                                listOf(VacuumFanPower.QUIET to "Quiet", VacuumFanPower.BALANCED to "Standard", VacuumFanPower.TURBO to "Turbo", VacuumFanPower.MAX to "Max")) { draft = it }
                            if (draftMode != ScheduleCleanMode.VACUUM) ScheduleChoice("Water", "water_box_mode", job, draft,
                                listOf(WaterBoxMode.LOW to "Low", WaterBoxMode.MEDIUM to "Medium", WaterBoxMode.HIGH to "High")) { draft = it }
                        }
                        ScheduleChoice("Passes", "repeat", job, draft, listOf(1 to "1", 2 to "2", 3 to "3")) { draft = it }
                        TextButton(onClick = { draft = emptyMap() }) { Text("Keep saved settings") }
                        Button(onClick = { changes = draft; sheet = null }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleChoice(label: String, key: String, job: ServerSchedule?, changes: Map<String, JsonElement>,
    options: List<Pair<Int, String>>, onChange: (Map<String, JsonElement>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = (changes[key] as? JsonPrimitive)?.contentOrNull ?: job?.settingsText(key).orEmpty()
    Box {
        ScheduleRow(label, options.firstOrNull { it.first.toString() == current }?.second
            ?: if (current.isBlank()) "Robot default" else "Saved setting") { expanded = true }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (number, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                onChange(changes + (key to JsonPrimitive(number))); expanded = false
            }) }
        }
    }
}
