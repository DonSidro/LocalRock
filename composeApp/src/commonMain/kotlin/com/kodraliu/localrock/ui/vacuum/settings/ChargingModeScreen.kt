package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.ClockStepper
import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.ValleySettings
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.coroutines.delay

/** Charging Mode: Smart Charging (off-peak timer closed) or Off-Peak Charging with its start and end. */
@Composable
internal fun ChargingModeScreen(
    valleySettings: ValleySettings?,
    onOffPeak: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit,
    onSmart: () -> Unit,
    onBack: () -> Unit,
) {
    val settings = valleySettings ?: ValleySettings()
    var offPeak by remember(valleySettings?.enabled) { mutableStateOf(settings.enabled) }
    var startHour by remember(valleySettings) { mutableStateOf(settings.startHour) }
    var startMinute by remember(valleySettings) { mutableStateOf(settings.startMinute) }
    var endHour by remember(valleySettings) { mutableStateOf(settings.endHour) }
    var endMinute by remember(valleySettings) { mutableStateOf(settings.endMinute) }
    val sameTime = startHour == endHour && startMinute == endMinute

    // Each stepper tap changes a time, so the robot is written once the taps stop; the same start and end is never sent.
    var timesTouched by remember { mutableStateOf(false) }
    LaunchedEffect(startHour, startMinute, endHour, endMinute) {
        if (!timesTouched || !offPeak || sameTime) return@LaunchedEffect
        delay(800)
        onOffPeak(startHour, startMinute, endHour, endMinute)
    }

    SettingsPage(title = SettingsStrings.CHARGING_MODE, onBack = onBack) {
        item {
            ModeCard(SettingsStrings.SMART_CHARGING, SettingsStrings.SMART_CHARGING_DETAIL, selected = !offPeak) {
                if (offPeak) { offPeak = false; onSmart() }
            }
        }
        item {
            ModeCard(SettingsStrings.OFF_PEAK_CHARGING, SettingsStrings.OFF_PEAK_CHARGING_DETAIL, selected = offPeak) {
                if (!offPeak && !sameTime) { offPeak = true; onOffPeak(startHour, startMinute, endHour, endMinute) }
            }
        }
        if (offPeak) {
            item {
                SettingGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(SettingsStrings.START, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ClockStepper(startHour, startMinute, onHourChange = { startHour = it; timesTouched = true }, onMinuteChange = { startMinute = it; timesTouched = true })
                        Text(SettingsStrings.END, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ClockStepper(endHour, endMinute, onHourChange = { endHour = it; timesTouched = true }, onMinuteChange = { endMinute = it; timesTouched = true })
                    }
                }
                if (sameTime) GroupFootnote(SettingsStrings.SAME_TIME_WARNING)
            }
        } else if (valleySettings == null) {
            item { GroupFootnote(SettingsStrings.OFF_PEAK_NOT_SET) }
        }
    }
}

/** One radio card: title, the official description and a radio button. */
@Composable
private fun ModeCard(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    SettingGroup {
        Surface(onClick = onClick, color = Color.Transparent) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(selected = selected, onClick = onClick)
            }
        }
    }
}

@Composable
fun VacuumChargingModeScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val valleySettings by repository.valleySettings.collectAsState()
    LaunchedOnEntry { runCatching { repository.fetchValleySettings() } }
    ChargingModeScreen(
        valleySettings = valleySettings,
        onOffPeak = { sh, sm, eh, em -> viewModel.run { it.setValley(sh, sm, eh, em) } },
        onSmart = { viewModel.run { it.disableValley() } },
        onBack = onBack,
    )
}
