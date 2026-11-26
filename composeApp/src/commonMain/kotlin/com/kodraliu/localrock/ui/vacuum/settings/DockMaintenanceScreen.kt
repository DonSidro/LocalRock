package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** Dock Maintenance: the cleaning tray self-cleaning card, the only part LocalRock can drive. */
@Composable
internal fun DockMaintenanceScreen(endsTask: Boolean, busy: Boolean, onStartSelfClean: (Boolean) -> Unit, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SettingsPage(title = SettingsStrings.DOCK_MAINTENANCE, onBack = onBack) {
        item {
            SettingGroup {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(SettingsStrings.TRAY_SELF_CLEAN, style = MaterialTheme.typography.titleMedium)
                        Text(SettingsStrings.TRAY_SELF_CLEAN_DETAIL, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { confirm = true }, enabled = !busy) { Text(SettingsStrings.TRAY_SELF_CLEAN_START) }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(SettingsStrings.TRAY_SELF_CLEAN_CONFIRM_TITLE) },
            text = { Text(if (endsTask) SettingsStrings.TRAY_SELF_CLEAN_IN_TASK else SettingsStrings.TRAY_SELF_CLEAN_CONFIRM_TEXT) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onStartSelfClean(endsTask) }) { Text(SettingsStrings.TRAY_SELF_CLEAN_START) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(SettingsStrings.CANCEL) } },
        )
    }
}

@Composable
fun VacuumDockMaintenanceScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    DockMaintenanceScreen(
        endsTask = traySelfCleanEndsTask(status.state),
        busy = busy,
        onStartSelfClean = { endTask -> viewModel.run(SettingsStrings.TRAY_SELF_CLEAN_STARTED) { it.startTraySelfClean(endTask) } },
        onBack = onBack,
    )
}

/** Drain On-Board Water Tank: the official checklist, then Start behind a confirmation. */
@Composable
internal fun DrainTankScreen(busy: Boolean, onStart: () -> Unit, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SettingsPage(title = SettingsStrings.DRAIN_ONBOARD_TANK, onBack = onBack) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.WaterDrop, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Column(Modifier.padding(horizontal = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(SettingsStrings.DRAIN_HINT, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf(SettingsStrings.DRAIN_TIP_DOCKED, SettingsStrings.DRAIN_TIP_COMPONENTS, SettingsStrings.DRAIN_TIP_NO_TASKS).forEach {
                    Text("· $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(SettingsStrings.DRAIN_DURATION, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(SettingsStrings.DRAIN_CONFIRM) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(SettingsStrings.DRAIN_CONFIRM_TITLE) },
            text = { Text(SettingsStrings.DRAIN_CONFIRM_TEXT) },
            confirmButton = { TextButton(onClick = { confirm = false; onStart() }) { Text(SettingsStrings.DRAIN_CONFIRM) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(SettingsStrings.CANCEL) } },
        )
    }
}

@Composable
fun VacuumDrainTankScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val busy by viewModel.busy.collectAsState()
    DrainTankScreen(
        busy = busy,
        onStart = { viewModel.run(SettingsStrings.DRAIN_STARTED) { it.emptyInbuiltWaterTank() } },
        onBack = onBack,
    )
}
