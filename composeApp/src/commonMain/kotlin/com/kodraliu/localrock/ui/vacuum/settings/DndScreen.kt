package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.DndSettings
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.strings.SettingsStrings
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DndScreen(
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
                title = { Text(SettingsStrings.DND) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
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
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, Modifier.size(20.dp)) }
                Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                IconButton(
                    onClick = { val next = value + step; onValueChange(if (next > range.last) range.first else next) },
                    modifier = Modifier.size(40.dp),
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
fun VacuumDndScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val dndSettings by repository.dndSettings.collectAsState()
    LaunchedOnEntry { runCatching { repository.fetchDndSettings() } }
    DndScreen(
        dndSettings = dndSettings,
        onEnable = { sh, sm, eh, em -> scope.launch { runCatching { repository.setDnd(sh, sm, eh, em) } } },
        onDisable = { scope.launch { runCatching { repository.disableDnd() } } },
        onBack = onBack,
    )
}
