package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
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

    suspend fun refresh() {
        jobs = api.list(repository.duid)
        loaded = true
        nativeStates = emptyMap()
        if (jobs.any { it.nativeName != null }) {
            try {
                val reply = repository.session.sendCommand("get_server_timer").requireOk("get_server_timer")
                nativeStates = jobs.mapNotNull { job -> job.nativeName?.let { job.id to nativeTimerState(reply.result, it) } }.toMap()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { notice = "Schedules loaded. Robot timer state could not be checked." }
        }
    }
    fun action(block: suspend () -> Unit) {
        if (busy || viewModel.scheduleDemoMode) return
        busy = true; error = null; notice = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                error = if (e is ScheduleException || e is IllegalArgumentException) e.message
                    else "Request could not be confirmed. Refresh before retrying; a write may already have reached the server."
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { action { refresh() } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Schedules") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Server schedules", style = MaterialTheme.typography.titleLarge)
            Text("The local server must be running at the scheduled time. Imported schedules also require their robot timer to be on.")
            if (viewModel.scheduleDemoMode) Text("Server schedules are unavailable in demo mode.")
            Row {
                TextButton(enabled = !busy && !viewModel.scheduleDemoMode, onClick = { action { refresh() } }) { Text("Refresh") }
                TextButton(enabled = loaded && !busy, onClick = { creating = true; error = null }) { Text("New schedule") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            notice?.let { Text(it) }
            if (loaded && jobs.isEmpty()) Text("No server schedules found.")
            jobs.forEach { job ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val time = job.weekly
                        Text(time?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
                            ?: job.cron, style = MaterialTheme.typography.titleLarge)
                        Text(time?.days?.sorted()?.joinToString(", ") { DAY_NAMES[it] } ?: "One-time or advanced schedule")
                        Text(job.timezone.ifBlank { "Server timezone" })
                        Text("Rooms: ${job.settingsText("segments").ifBlank { "Unspecified" }.let { if (it == "0") "Whole home" else it }}")
                        job.mapIndex?.let { Text("Saved map index: $it") }
                        job.nextFire?.let { Text("Next (UTC): $it", style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Enabled on server", Modifier.weight(1f))
                            Switch(checked = job.enabled, enabled = !busy && (job.repeated || job.enabled), onCheckedChange = { enabled ->
                                action {
                                    api.update(repository.duid, job, buildJsonObject { put("enabled", enabled) })
                                    refresh()
                                }
                            })
                        }
                        if (job.nativeName != null) Text("Robot timer: ${nativeStates[job.id] ?: "Not checked"}")
                        if (!job.editable) Text("Time and settings are read-only for this schedule format.")
                        Row {
                            TextButton(enabled = !busy && job.editable, onClick = { editing = job; error = null }) { Text("Edit") }
                            TextButton(enabled = !busy, onClick = { deleting = job; error = null }) { Text("Delete") }
                        }
                    }
                }
            }
            TextButton(enabled = !busy, onClick = { legacy = true }) { Text("Legacy timers") }
        }
    }
    if (creating || editing != null) {
        ScheduleEditor(editing, rooms, busy, error,
            onDismiss = { if (!busy) { creating = false; editing = null; error = null } },
            onSave = { body ->
                val existing = editing
                action {
                    if (existing == null) api.create(repository.duid, body) else api.update(repository.duid, existing, body)
                    creating = false; editing = null
                    notice = "Schedule saved."
                    refresh()
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

private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@Composable
private fun ScheduleEditor(job: ServerSchedule?, rooms: List<ParsedMapRoom>, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (JsonObject) -> Unit) {
    val initial = job?.weekly ?: WeeklyTime(8, 0, setOf(1, 2, 3, 4, 5))
    var hour by remember { mutableStateOf(initial.hour.toString().padStart(2, '0')) }
    var minute by remember { mutableStateOf(initial.minute.toString().padStart(2, '0')) }
    var days by remember { mutableStateOf(initial.days) }
    var zone by remember { mutableStateOf(job?.timezone.orEmpty()) }
    var roomEditing by remember { mutableStateOf(false) }
    var wholeHome by remember { mutableStateOf(job == null || job.settingsText("segments") == "0") }
    var selected by remember { mutableStateOf(job?.settingsText("segments").orEmpty().split(',', ';').mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } }) }
    var changes by remember { mutableStateOf<Map<String, JsonElement>>(emptyMap()) }
    var localError by remember { mutableStateOf<String?>(null) }
    // Stable across a failed POST, so retry cannot create a duplicate with a new identity.
    val creationId = remember { Random.nextLong(1, Long.MAX_VALUE) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (job == null) "New server schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hour, { hour = it }, label = { Text("Hour (0–23)") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                    OutlinedTextField(minute, { minute = it }, label = { Text("Minute") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
                }
                DAY_NAMES.forEachIndexed { day, label ->
                    Row { Checkbox(day in days, { checked -> days = if (checked) days + day else days - day }, enabled = !busy); Text(label, Modifier.padding(top = 12.dp)) }
                }
                OutlinedTextField(zone, { zone = it }, label = { Text("Timezone") }, placeholder = { Text("America/Los_Angeles") }, singleLine = true, enabled = !busy)
                Text("Use an IANA timezone. Times follow that zone's daylight-saving rules.")
                if (job == null) Text("Created disabled. New schedules run on the currently loaded map; keep that map loaded for room cleans.")
                else Text("Existing rooms and cleaning settings are preserved unless changed below.")
                Row {
                    Checkbox(roomEditing, { roomEditing = it }, enabled = !busy)
                    Text("Change rooms: I have loaded this schedule's map", Modifier.weight(1f))
                }
                if (roomEditing) {
                    Row { Checkbox(wholeHome, { wholeHome = it }, enabled = !busy); Text("Whole home") }
                    if (!wholeHome) {
                        if (rooms.isEmpty()) Text("No rooms loaded. Open the map first.")
                        rooms.forEach { room -> Row {
                            Checkbox(room.id in selected, { checked -> selected = if (checked) (selected + room.id).distinct() else selected - room.id }, enabled = !busy)
                            Text("${room.name} (${room.id})", Modifier.weight(1f))
                        } }
                        val unknown = selected.filter { id -> rooms.none { it.id == id } }
                        if (unknown.isNotEmpty()) Text("Rooms not on the loaded map: ${unknown.joinToString()}. Select the correct map before changing rooms.")
                    }
                }
                ScheduleChoice("Suction", "fan_power", job, changes,
                    listOf(VacuumFanPower.QUIET to "Quiet", VacuumFanPower.BALANCED to "Standard", VacuumFanPower.TURBO to "Strong", VacuumFanPower.MAX to "Max"), !busy) { changes = it }
                ScheduleChoice("Water", "water_box_mode", job, changes,
                    listOf(WaterBoxMode.OFF to "Off", WaterBoxMode.LOW to "Low", WaterBoxMode.MEDIUM to "Medium", WaterBoxMode.HIGH to "High"), !busy) { changes = it }
                ScheduleChoice("Passes", "repeat", job, changes, listOf(1 to "1", 2 to "2", 3 to "3"), !busy) { changes = it }
                (localError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            try {
                val time = WeeklyTime(hour.toIntOrNull() ?: -1, minute.toIntOrNull() ?: -1, days)
                time.cron()
                val settings = changes.toMutableMap()
                if (roomEditing) {
                    require(wholeHome || (selected.isNotEmpty() && selected.all { id -> rooms.any { it.id == id } })) { "Select rooms from the correct loaded map" }
                    settings["segments"] = JsonPrimitive(if (wholeHome) "0" else selected.joinToString(","))
                }
                val payload = job?.update(time, zone, job.enabled, settings)
                    ?: JsonObject(ServerSchedule.create(time, zone, settings).toMutableMap().apply { put("id", JsonPrimitive(creationId)) })
                localError = null
                onSave(payload)
            } catch (e: IllegalArgumentException) { localError = e.message }
        }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ScheduleChoice(label: String, key: String, job: ServerSchedule?, changes: Map<String, JsonElement>,
    options: List<Pair<Int, String>>, enabled: Boolean, onChange: (Map<String, JsonElement>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val value = (changes[key] as? JsonPrimitive)?.intOrNull
    val current = job?.settingsText(key).orEmpty()
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(enabled = enabled, onClick = { expanded = true }) {
                Text(options.firstOrNull { it.first == value }?.second ?: if (current.isBlank()) "Robot default" else "Keep current ($current)")
            }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(text = { Text("Keep existing / default") }, onClick = { onChange(changes - key); expanded = false })
                options.forEach { (number, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                    onChange(changes + (key to JsonPrimitive(number))); expanded = false
                }) }
            }
        }
    }
}
