package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.http.currentEpochSeconds
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingAmber
import com.kodraliu.localrock.ui.components.SettingsCategoryRow
import com.kodraliu.localrock.ui.navigation.VacuumFirmware
import com.kodraliu.localrock.ui.navigation.VacuumHistory
import com.kodraliu.localrock.ui.navigation.VacuumMaintenance
import com.kodraliu.localrock.ui.navigation.VacuumMapEdit
import com.kodraliu.localrock.ui.navigation.VacuumPinGo
import com.kodraliu.localrock.ui.navigation.VacuumRemote
import com.kodraliu.localrock.ui.navigation.VacuumSchedule
import com.kodraliu.localrock.ui.navigation.VacuumSettingsBeta
import com.kodraliu.localrock.ui.navigation.VacuumSettingsCarpet
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDock
import com.kodraliu.localrock.ui.navigation.VacuumSettingsFloor
import com.kodraliu.localrock.ui.navigation.VacuumSettingsMatter
import com.kodraliu.localrock.ui.navigation.VacuumSettingsMoreOptions
import com.kodraliu.localrock.ui.navigation.VacuumSettingsObstacle
import com.kodraliu.localrock.ui.navigation.VacuumSettingsProductInfo
import com.kodraliu.localrock.ui.navigation.VacuumSettingsRobotVoice
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Settings root: a history header card, then the official seven untitled groups. */
@Composable
fun VacuumSettingsScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
    onNavigate: (Any) -> Unit,
) {
    val repository = viewModel.repository
    val caps by viewModel.capabilities.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    val timers by repository.timers.collectAsState()
    val consumables by repository.consumableStatus.collectAsState()
    val history by repository.cleanHistory.collectAsState()
    val robotSettings by repository.robotSettings.collectAsState()
    val newestRun by NewestRunCache.flow(repository).collectAsState()

    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry {
        runCatching { repository.fetchTimers() }
        runCatching { repository.fetchConsumableStatus() }
        // The robot preload already read the newest run; this is a silent refresh.
        if (repository.cleanHistory.value.isEmpty()) runCatching { NewestRunCache.load(repository) }
    }

    val now = Instant.fromEpochSeconds(currentEpochSeconds()).toLocalDateTime(TimeZone.currentSystemDefault())
    val (duration, area) = lastCleanFigures(lastCleanRecord(history) ?: newestRun)
    // Decided from what the robot advertises, known on open, so these rows never pop in after the reads.
    val floorRows = viewModel.betaFeatureDefs.collectAsState().value.any { it.section == FeatureSection.FLOOR } || floorFeatureGroups(betaFeatures).isNotEmpty()
    val obstacleRows = viewModel.betaFeatureDefs.collectAsState().value.any { it.section == FeatureSection.OBSTACLE } || betaFeatures.inSection(FeatureSection.OBSTACLE).isNotEmpty()

    SettingsPage(title = SettingsStrings.TITLE, onBack = onBack) {
        item { HistoryHeaderCard(duration, area, onClick = { onNavigate(VacuumHistory) }) }
        item {
            SettingGroup {
                SettingsCategoryRow(Icons.Default.Schedule, AppColors.AccentIndigo, SettingsStrings.SCHEDULES,
                    schedulesDetail(nextCleaningToday(timers, now))) { onNavigate(VacuumSchedule) }
            }
        }
        item {
            SettingGroup {
                SettingsCategoryRow(Icons.Default.Build, if (suppliesDue(consumables)) AppColors.Warn else AppColors.Good,
                    SettingsStrings.MAINTENANCE, maintenanceDetail(consumables)) { onNavigate(VacuumMaintenance) }
            }
        }
        val cleaningRows = listOfNotNull(
            RootRow(Icons.Default.GridView, AppColors.Good, SettingsStrings.CARPET, SettingsStrings.CARPET_DETAIL, VacuumSettingsCarpet)
                .takeIf { caps.hasCarpetSettings },
            RootRow(Icons.Default.CleaningServices, AppColors.AccentCyan, SettingsStrings.FLOOR, SettingsStrings.FLOOR_DETAIL, VacuumSettingsFloor)
                .takeIf { floorRows },
            RootRow(Icons.Default.Home, AppColors.Water, SettingsStrings.DOCK, SettingsStrings.DOCK_DETAIL, VacuumSettingsDock)
                .takeIf { caps.hasDockSettings },
            RootRow(Icons.Default.Sensors, SettingAmber, SettingsStrings.OBSTACLE, SettingsStrings.OBSTACLE_DETAIL, VacuumSettingsObstacle)
                .takeIf { caps.hasObstacleSettings || obstacleRows },
        )
        if (cleaningRows.isNotEmpty()) {
            item {
                SettingGroup {
                    cleaningRows.forEachIndexed { i, row ->
                        if (i > 0) RowDivider()
                        SettingsCategoryRow(row.icon, row.tint, row.title, row.detail) { onNavigate(row.route) }
                    }
                }
            }
        }
        item {
            SettingGroup {
                SettingsCategoryRow(Icons.Default.Map, AppColors.AccentCyan, SettingsStrings.MANAGE_MAPS, SettingsStrings.MANAGE_MAPS_DETAIL) {
                    onNavigate(VacuumMapEdit)
                }
                RowDivider()
                SettingsCategoryRow(
                    Icons.Default.RecordVoiceOver, AppColors.Water,
                    if (caps.voiceAssistant) SettingsStrings.VOICE_ASSISTANT else SettingsStrings.ROBOT_VOICE,
                    robotVoiceDetail(robotSettings.volume),
                ) { onNavigate(VacuumSettingsRobotVoice) }
                if (caps.matter) {
                    RowDivider()
                    SettingsCategoryRow(Icons.Default.Link, AppColors.AccentIndigo, SettingsStrings.MATTER, SettingsStrings.MATTER_DETAIL) {
                        onNavigate(VacuumSettingsMatter)
                    }
                }
                RowDivider()
                SettingsCategoryRow(Icons.Default.Tune, AppColors.AccentPurple, SettingsStrings.MORE_OPTIONS, SettingsStrings.MORE_OPTIONS_DETAIL) {
                    onNavigate(VacuumSettingsMoreOptions)
                }
            }
        }
        if (caps.pinNGo || caps.remoteControl) {
            item {
                SettingGroup {
                    if (caps.pinNGo) {
                        SettingsCategoryRow(Icons.Default.PinDrop, AppColors.Good, SettingsStrings.PIN_N_GO, SettingsStrings.PIN_N_GO_DETAIL) {
                            onNavigate(VacuumPinGo)
                        }
                    }
                    if (caps.pinNGo && caps.remoteControl) RowDivider()
                    if (caps.remoteControl) {
                        SettingsCategoryRow(Icons.Default.Gamepad, AppColors.AccentCyan, SettingsStrings.REMOTE_CONTROL, SettingsStrings.REMOTE_CONTROL_DETAIL) {
                            onNavigate(VacuumRemote)
                        }
                    }
                }
            }
        }
        item {
            // No User Manual row: LocalRock has no manual to open.
            SettingGroup {
                SettingsCategoryRow(Icons.Default.Info, AppColors.AccentIndigo, SettingsStrings.PRODUCT_INFO, SettingsStrings.PRODUCT_INFO_DETAIL) {
                    onNavigate(VacuumSettingsProductInfo)
                }
            }
        }
        item {
            SettingGroup {
                if (caps.firmwareUpdates) {
                    SettingsCategoryRow(
                        Icons.Default.SystemUpdate, AppColors.AccentCyan, SettingsStrings.FIRMWARE_UPDATES,
                        viewModel.firmwareVersion ?: SettingsStrings.FIRMWARE_DETAIL,
                    ) { onNavigate(VacuumFirmware) }
                }
                if (caps.firmwareUpdates && caps.showsBetaFeatures) RowDivider()
                if (caps.showsBetaFeatures) {
                    SettingsCategoryRow(Icons.Default.Science, AppColors.AccentCyan, SettingsStrings.BETA_FEATURES, SettingsStrings.BETA_FEATURES_DETAIL) {
                        onNavigate(VacuumSettingsBeta)
                    }
                }
            }
        }
    }
}

private class RootRow(val icon: ImageVector, val tint: Color, val title: String, val detail: String, val route: Any)

/** Last run's duration and area; opens History. */
@Composable
private fun HistoryHeaderCard(duration: String, area: String, onClick: () -> Unit) {
    SettingGroup {
        Surface(onClick = onClick, color = Color.Transparent) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HeaderFigure(duration, SettingsStrings.HISTORY_LAST_DURATION, Modifier.weight(1f))
                HeaderFigure(area, SettingsStrings.HISTORY_LAST_AREA, Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, SettingsStrings.HISTORY, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeaderFigure(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
