package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingAmber
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.components.SettingsCategoryRow
import com.kodraliu.localrock.ui.navigation.VacuumSettingsChargingMode
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDnd
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.coroutines.launch

/** More Options, in the official order: DND, Charging Mode, lights and child lock, Adjusted Battery Level, Find Robot. */
@Composable
fun VacuumMoreOptionsScreen(viewModel: VacuumViewModel, onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val caps by viewModel.capabilities.collectAsState()
    val robotSettings by repository.robotSettings.collectAsState()
    val dnd by repository.dndSettings.collectAsState()
    val valley by repository.valleySettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    var findTriggered by remember { mutableStateOf(false) }

    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry {
        runCatching { repository.fetchRobotSettings() }
        runCatching { repository.fetchDndSettings() }
        if (caps.chargingMode) runCatching { repository.fetchValleySettings() }
    }
    val onToggle: (String, Boolean) -> Unit = { id, on -> viewModel.report { it.setBetaFeature(id, on) } }
    val lightRows = betaFeatures.robotRows(RobotRowPlace.LIGHTS)
    val batteryRow = betaFeatures.robotRows(RobotRowPlace.ADJUSTED_BATTERY).firstOrNull()
    val otherRows = betaFeatures.robotRows(RobotRowPlace.OTHER)

    SettingsPage(title = SettingsStrings.MORE_OPTIONS, onBack = onBack) {
        item {
            SettingGroup {
                SettingsCategoryRow(
                    Icons.Default.DoNotDisturbOn, AppColors.AccentIndigo, SettingsStrings.DND,
                    if (dnd?.enabled == true) SettingsStrings.ON else SettingsStrings.OFF,
                ) { onNavigate(VacuumSettingsDnd) }
            }
            GroupFootnote(SettingsStrings.DND_DETAIL)
        }
        if (caps.chargingMode) {
            item {
                SettingGroup {
                    SettingsCategoryRow(
                        Icons.Default.BatteryChargingFull, AppColors.Good, SettingsStrings.CHARGING_MODE,
                        if (valley?.enabled == true) SettingsStrings.OFF_PEAK_CHARGING else SettingsStrings.SMART_CHARGING,
                    ) { onNavigate(VacuumSettingsChargingMode) }
                }
            }
        }
        item {
            SettingGroup {
                // set_led_status is the plugin's Button Lights; Status Indicator Light is get/set_flow_led_status, which neither test robot knows.
                SettingRowSwitch(
                    icon = Icons.Default.Lightbulb, iconTint = SettingAmber,
                    title = SettingsStrings.BUTTON_LIGHTS, subtitle = SettingsStrings.BUTTON_LIGHTS_DETAIL,
                    checked = robotSettings.ledEnabled,
                    onCheckedChange = { on -> viewModel.report { it.setLed(on) } },
                )
                lightRows.forEach { f ->
                    RowDivider()
                    FeatureToggleRow(f, onToggle)
                }
                if (caps.childLock) {
                    RowDivider()
                    SettingRowSwitch(
                        icon = Icons.Default.Lock, iconTint = AppColors.Warn,
                        title = SettingsStrings.CHILD_LOCK, subtitle = SettingsStrings.CHILD_LOCK_DETAIL,
                        checked = robotSettings.childLock,
                        onCheckedChange = { locked -> viewModel.report { it.setChildLock(locked) } },
                    )
                }
            }
        }
        // Shown only when the robot answers get_optimize_battery_status; neither test robot does.
        if (batteryRow != null) {
            item {
                SettingGroup {
                    SettingRowSwitch(
                        icon = Icons.Default.BatteryStd, iconTint = AppColors.Good,
                        title = SettingsStrings.ADJUSTED_BATTERY_LEVEL, subtitle = SettingsStrings.ADJUSTED_BATTERY_LEVEL_DETAIL,
                        checked = batteryRow.enabled,
                        onCheckedChange = { onToggle(batteryRow.def.id, it) },
                    )
                }
            }
        }
        item {
            SettingGroup {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilledTonalButton(onClick = {
                        findTriggered = true
                        scope.launch { runCatching { repository.findMe() } }
                    }) { Text(SettingsStrings.FIND_ROBOT) }
                    if (findTriggered) {
                        Text(SettingsStrings.FIND_ROBOT_RINGING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (otherRows.isNotEmpty()) {
            item { GroupCaption(SettingsStrings.MORE_ROBOT_OPTIONS, BonusKind.LocalRock) }
            item {
                SettingGroup {
                    otherRows.forEachIndexed { i, f ->
                        if (i > 0) RowDivider()
                        FeatureToggleRow(f, onToggle)
                    }
                }
            }
        }
    }
}
