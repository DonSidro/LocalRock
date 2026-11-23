package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockOptions
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** Washing Mode: the official page with the wash water volume and, when reported, the wash temperature. */
@Composable
internal fun WashingModeScreen(
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    dockType: Int?,
    dockStatus: Int?,
    busy: Boolean,
    onWashModeChange: (Int) -> Unit,
    onWashWaterTemperatureChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val washModes = dockCapabilities.washModes(dockType, dockStatus)
    val washMode = dockCapabilities.washModeCode(dockSettings.washMode, dockType)
    val washModeEnabled = !busy && dockType != null && dockType != 0 && dockSettings.washMode != null
    val temperature = DockOptions.washWaterTemperatureCode(dockSettings.washWaterTemperature, dockType)

    SettingsPage(title = SettingsStrings.WASHING_MODE, onBack = onBack) {
        if (washModes.isNotEmpty()) {
            item { GroupCaption(SettingsStrings.WASH_WATER_VOLUME) }
            item { DockOptionGroup(washModeCards(washModes), washMode, washModeEnabled, onWashModeChange) }
        }
        if (temperature != null) {
            item { GroupCaption(SettingsStrings.MOP_WASH_TEMPERATURE) }
            item { DockOptionGroup(washTemperatureCards(dockType), temperature, !busy, onWashWaterTemperatureChange) }
        }
    }
}

/** One card of radio rows; picking the selected row again writes nothing. */
@Composable
private fun DockOptionGroup(cards: List<DockOptionCard>, selected: Int?, enabled: Boolean, onSelect: (Int) -> Unit) {
    SettingGroup {
        cards.forEachIndexed { i, card ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = card.value == selected, enabled = enabled, role = Role.RadioButton) {
                        if (card.value != selected) onSelect(card.value)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.title, style = MaterialTheme.typography.titleMedium)
                    card.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                RadioButton(selected = card.value == selected, enabled = enabled, onClick = null)
            }
        }
    }
}

@Composable
fun VacuumWashingModeScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val dockSettings by repository.dockSettings.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    LaunchedOnEntry { runCatching { repository.fetchDockSettings() } }
    WashingModeScreen(
        dockSettings = dockSettings,
        dockCapabilities = viewModel.dockCapabilities.collectAsState().value,
        dockType = status.dockType,
        dockStatus = status.dockStatus,
        busy = busy,
        onWashModeChange = { mode -> viewModel.run { it.setWashMode(mode) } },
        onWashWaterTemperatureChange = { value -> viewModel.run { it.setWashWaterTemperature(value) } },
        onBack = onBack,
    )
}
