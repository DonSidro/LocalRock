package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Plumbing
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.DockOptions
import com.kodraliu.localrock.shared.vacuum.DockSettings
import com.kodraliu.localrock.shared.vacuum.washFrequencyLabel
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.PickerDialog
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.components.SettingRowClickable
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDockMaintenance
import com.kodraliu.localrock.ui.navigation.VacuumSettingsDrainTank
import com.kodraliu.localrock.ui.navigation.VacuumSettingsWashingMode
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** Dock Settings in the official four groups: washing, emptying, drying, dock maintenance. */
@Composable
internal fun DockSettingsScreen(
    dockSettings: DockSettings,
    dockCapabilities: DockCapabilities,
    dockType: Int?,
    dockStatus: Int?,
    busy: Boolean,
    onOpenWashingMode: () -> Unit,
    onOpenDockMaintenance: () -> Unit,
    onOpenDrainTank: () -> Unit,
    onWashFrequencyChange: (Int, Int?) -> Unit,
    onAutoEmptyModeChange: (Int) -> Unit,
    features: List<BetaFeatureState>,
    onFeatureToggle: (String, Boolean) -> Unit,
    onFeatureChoice: (String, Int) -> Unit,
    onMopDryingChange: (Boolean) -> Unit,
    onDryTimeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var dialogTarget by remember { mutableStateOf<String?>(null) }

    val washFrequencyOptions = dockCapabilities.washFrequencyOptions
    val dryTimeCards = dryTimeCards(dockCapabilities.namedDryTimeOptions(dockSettings.dryTimeSeconds))
    val smartWash = dockSettings.smartWash
    val washFrequencyIndex = washFrequencyOptions.indexOfFirst {
        smartWash != null && it.mode == smartWash.mode && (it.mode != 0 || it.intervalMinutes?.times(60) == smartWash.intervalSeconds)
    }
    val washModes = dockCapabilities.washModes(dockType, dockStatus)
    val autoEmptyModes = dockCapabilities.autoEmptyModes
    val dockKnown = dockType != null && dockType != 0
    val washFrequencyEnabled = !busy && dockKnown && smartWash != null && washFrequencyOptions.isNotEmpty()
    val washModeEnabled = !busy && dockKnown && dockSettings.washMode != null && washModes.isNotEmpty()
    // The page also holds Mop Washing Temperature, so it opens when either group can be used.
    val washingPageEnabled = washModeEnabled || (!busy && dockSettings.washWaterTemperature != null)
    val autoEmptyEnabled = !busy && dockSettings.autoEmptyMode != null && autoEmptyModes.isNotEmpty()
    val rows = dockPageRows(features, dockSettings.mopDrying, dockStatus)
    val traySelfClean = DockOptions.traySelfCleanSupported(dockType, dockStatus)
    val drainTank = dockCapabilities.drainOnboardTankSupported
    val dryTime = dockSettings.dryTimeSeconds.takeIf { dockSettings.mopDrying != null }
    val showDryGroup = rows.autoDryingSwitch || rows.dryingStartMethod != null || dryTime != null || rows.dryOther.isNotEmpty()

    SettingsPage(title = SettingsStrings.DOCK, onBack = onBack) {
        item {
            SettingGroup {
                SettingRowClickable(Icons.Default.Repeat, AppColors.Water, SettingsStrings.MOP_WASH_FREQUENCY,
                    SettingsStrings.MOP_WASH_FREQUENCY_DETAIL, enabled = washFrequencyEnabled,
                    value = washFrequencyLabel(smartWash)) { dialogTarget = "wash_freq" }
                RowDivider()
                SettingRowClickable(Icons.Default.LocalLaundryService, AppColors.AccentCyan, SettingsStrings.WASHING_MODE,
                    SettingsStrings.WASHING_MODE_DETAIL, enabled = washingPageEnabled,
                    value = dockCapabilities.washingModeValue(dockSettings.washMode, dockType, dockSettings.washWaterTemperature)) { onOpenWashingMode() }
                rows.wash.forEach { RowDivider(); FeatureToggleRow(it, onFeatureToggle, onFeatureChoice, valueAtEnd = true) }
            }
        }
        item {
            SettingGroup {
                rows.empty.forEach { FeatureToggleRow(it, onFeatureToggle, onFeatureChoice, valueAtEnd = true); RowDivider() }
                SettingRowClickable(Icons.Default.Delete, AppColors.Good, SettingsStrings.EMPTY_MODE, null, enabled = autoEmptyEnabled,
                    value = dockCapabilities.autoEmptyModeLabel(dockSettings.autoEmptyMode)) { dialogTarget = "auto_empty" }
            }
        }
        if (showDryGroup) {
            item {
                SettingGroup {
                    var first = true
                    if (rows.autoDryingSwitch) {
                        if (!first) RowDivider(); first = false
                        val drying = dockSettings.mopDrying == true
                        SettingRowSwitch(
                            icon = Icons.Default.Air, iconTint = AppColors.AccentCyan, title = SettingsStrings.AUTO_DRYING,
                            subtitle = SettingsStrings.AUTO_DRYING_DETAIL, checked = drying, enabled = !busy, onCheckedChange = onMopDryingChange,
                        )
                    }
                    rows.dryingStartMethod?.let { if (!first) RowDivider(); first = false; FeatureToggleRow(it, onFeatureToggle, onFeatureChoice, valueAtEnd = true) }
                    dryTime?.let { seconds ->
                        if (!first) RowDivider(); first = false
                        SettingRowClickable(Icons.Default.Schedule, AppColors.AccentCyan, SettingsStrings.DRYING_DURATION, null,
                            enabled = !busy, value = dockCapabilities.dryTimeName(seconds)) { dialogTarget = "dry_time" }
                    }
                    rows.dryOther.forEach { if (!first) RowDivider(); first = false; FeatureToggleRow(it, onFeatureToggle, onFeatureChoice, valueAtEnd = true) }
                }
            }
        }
        if (traySelfClean || drainTank || rows.maintenance.isNotEmpty()) {
            item {
                SettingGroup {
                    var first = true
                    if (traySelfClean) {
                        if (!first) RowDivider(); first = false
                        SettingRowClickable(Icons.Default.Build, AppColors.Water, SettingsStrings.DOCK_MAINTENANCE, null) { onOpenDockMaintenance() }
                    }
                    if (drainTank) {
                        if (!first) RowDivider(); first = false
                        SettingRowClickable(Icons.Default.Plumbing, AppColors.Water, SettingsStrings.DRAIN_ONBOARD_TANK, null) { onOpenDrainTank() }
                    }
                    rows.maintenance.forEach { if (!first) RowDivider(); first = false; FeatureToggleRow(it, onFeatureToggle, onFeatureChoice, valueAtEnd = true) }
                }
            }
        }
        if (rows.other.isNotEmpty()) item { FeatureCard(rows.other, onFeatureToggle, onFeatureChoice) }
    }

    when (dialogTarget) {
        "wash_freq" -> PickerDialog(
            title = SettingsStrings.MOP_WASH_FREQUENCY,
            options = washFrequencyOptions.map { it.label },
            selected = washFrequencyIndex,
            onSelect = {
                val option = washFrequencyOptions[it]
                onWashFrequencyChange(option.mode, option.intervalMinutes)
                dialogTarget = null
            },
            enabled = washFrequencyEnabled,
            onDismiss = { dialogTarget = null },
        )
        "dry_time" -> PickerDialog(
            title = SettingsStrings.DRYING_DURATION,
            options = dryTimeCards.map { it.title },
            selected = dryTimeCards.indexOfFirst { it.value == dockSettings.dryTimeSeconds },
            onSelect = { onDryTimeChange(dryTimeCards[it].value); dialogTarget = null },
            enabled = !busy,
            details = dryTimeCards.map { it.detail },
            onDismiss = { dialogTarget = null },
        )
        "auto_empty" -> PickerDialog(
            title = SettingsStrings.EMPTY_MODE,
            options = autoEmptyModes.map { it.second },
            selected = autoEmptyModes.indexOfFirst { it.first == dockSettings.autoEmptyMode },
            onSelect = { onAutoEmptyModeChange(autoEmptyModes[it].first); dialogTarget = null },
            enabled = autoEmptyEnabled,
            onDismiss = { dialogTarget = null },
        )
    }
}

/** Off-peak charging moved to More Options > Charging Mode, so this page no longer reads it. */
@Composable
fun VacuumDockSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit, onNavigate: (Any) -> Unit) {
    val repository = viewModel.repository
    val dockSettings by repository.dockSettings.collectAsState()
    val betaFeatures by repository.betaFeatures.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    LaunchedOnEntry { runCatching { repository.fetchDockSettings() } }
    DockSettingsScreen(
        dockSettings = dockSettings,
        dockCapabilities = viewModel.dockCapabilities.collectAsState().value,
        dockType = status.dockType,
        dockStatus = status.dockStatus,
        busy = busy,
        onOpenWashingMode = { onNavigate(VacuumSettingsWashingMode) },
        onOpenDockMaintenance = { onNavigate(VacuumSettingsDockMaintenance) },
        onOpenDrainTank = { onNavigate(VacuumSettingsDrainTank) },
        onWashFrequencyChange = { mode, minutes -> viewModel.run { it.setWashFrequency(mode, minutes) } },
        onAutoEmptyModeChange = { mode -> viewModel.run { it.setAutoEmptyMode(mode) } },
        features = betaFeatures,
        onFeatureToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
        // Drying Start Method writes the dryer setting, so the dryer fields re-read it.
        onFeatureChoice = { id, value ->
            viewModel.report { it.setBetaChoice(id, value); if (id == "drying_start_method") runCatching { it.fetchDockSettings() } }
        },
        onMopDryingChange = { on -> viewModel.run { it.setMopDrying(on) } },
        onDryTimeChange = { seconds -> viewModel.run { it.setDryTime(seconds) } },
        onBack = onBack,
    )
}
