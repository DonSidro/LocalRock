package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDamage
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.FeatureChoice
import com.kodraliu.localrock.shared.vacuum.choiceLabelFor
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import com.kodraliu.localrock.ui.components.SettingRowClickable
import androidx.compose.runtime.collectAsState
import com.kodraliu.localrock.ui.components.SettingGroup
import com.kodraliu.localrock.ui.strings.SettingsStrings

/** A per-feature icon so rows on one page are told apart at a glance; null falls back to the section icon. */
private fun featureIcon(id: String): ImageVector? = when (id) {
    "cleaning_pattern" -> Icons.Default.Route
    "identify_ground_material" -> Icons.Default.Layers
    "follow_ground_material" -> Icons.Default.Texture
    "corner_mop_stretch" -> Icons.Default.WaterDrop
    "right_brush_stretch" -> Icons.Default.OpenInFull
    "gap_deep_clean" -> Icons.Default.BorderOuter
    "pet_supplies_deep_clean" -> Icons.Default.Pets
    "dirty_object_detect" -> Icons.Default.Visibility
    "auto_remop" -> Icons.Default.Replay
    "uncleaned_area_reminder" -> Icons.Default.NotificationsActive
    "climbing_arms_high_sill" -> Icons.Default.Stairs
    "carpet_first" -> Icons.Default.LowPriority
    "carpet_boost" -> Icons.Default.Speed
    "carpet_deep_clean" -> Icons.Default.Grass
    "avoid_carpet_wires" -> Icons.Default.Cable
    "carpet_side_brush_lift" -> Icons.Default.VerticalAlignTop
    "avoid_collision", "less_collision_mode" -> Icons.Default.Shield
    "pet_snapshot" -> Icons.Default.PhotoCamera
    "obstacle_photos" -> Icons.Default.Image
    "pet_mode" -> Icons.Default.Pets
    "ai_environment" -> Icons.Default.Weekend
    "fill_light" -> Icons.Default.Lightbulb
    "follow_low_obstacles" -> Icons.Default.Height
    "identify_room" -> Icons.Default.MeetingRoom
    "clean_fluid_delivery" -> Icons.Default.Science
    "drying_start_method" -> Icons.Default.PlayCircle
    "dust_collection_switch", "dust_collection_interval" -> Icons.Default.Autorenew
    "water_leak_check" -> Icons.Default.WaterDamage
    "voice_control_led", "voice_assistant", "full_duplex" -> Icons.Default.RecordVoiceOver
    else -> null
}

/** One feature the robot reports, as a switch under the page it belongs to; [valueAtEnd] puts a picker's value after its description. */
@Composable
internal fun FeatureToggleRow(
    f: BetaFeatureState,
    onToggle: (String, Boolean) -> Unit,
    onChoice: (String, Int) -> Unit = { _, _ -> },
    valueAtEnd: Boolean = false,
) {
    val icon = featureIcon(f.def.id) ?: when (f.def.section) {
        FeatureSection.CARPET -> Icons.Default.GridView
        FeatureSection.FLOOR -> Icons.Default.CleaningServices
        FeatureSection.OBSTACLE -> Icons.Default.Sensors
        FeatureSection.DOCK -> Icons.Default.Home
        FeatureSection.ROBOT -> Icons.Default.Settings
        FeatureSection.BETA -> Icons.Default.Science
    }
    val choices = f.def.choices
    if (choices != null) {
        var showPicker by remember { mutableStateOf(false) }
        SettingRowClickable(
            icon = icon,
            iconTint = AppColors.AccentCyan,
            title = f.def.title,
            subtitle = if (valueAtEnd) f.def.description else f.choiceLabel ?: f.def.description,
            value = f.choiceLabel.takeIf { valueAtEnd },
            onClick = { showPicker = true },
        )
        if (showPicker) {
            ChoicePickerDialog(
                title = f.def.title,
                choices = choices,
                selected = f.value,
                onSelect = { onChoice(f.def.id, it); showPicker = false },
                onDismiss = { showPicker = false },
            )
        }
        return
    }
    // A switch that cannot turn on yet (Pet Snaps before its camera bits) shows why instead of its description.
    val blocked = f.blockedReason
    SettingRowSwitch(
        icon = icon,
        iconTint = AppColors.AccentCyan,
        title = f.def.title,
        subtitle = blocked ?: f.def.description,
        checked = f.enabled,
        enabled = blocked == null,
        onCheckedChange = { onToggle(f.def.id, it) },
    )
}

/** Radio list of a multi-choice setting's values; a current value with no listed choice shows as an extra "Other (n)" line. */
@Composable
private fun ChoicePickerDialog(
    title: String,
    choices: List<FeatureChoice>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val unknown = selected?.takeIf { v -> choices.none { it.value == v } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    // The whole row selects, not just the radio button.
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = choice.value == selected, role = Role.RadioButton) { onSelect(choice.value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                            choice.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (unknown != null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, enabled = false, onClick = {})
                        Spacer(Modifier.width(8.dp))
                        Text(choiceLabelFor(choices, unknown), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Floor Cleaning Setting: the robot's floor rows in the official groups, one card each. */
@Composable
fun VacuumFloorSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val betaFeatures by viewModel.repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    val onToggle: (String, Boolean) -> Unit = { id, on -> viewModel.report { it.setBetaFeature(id, on) } }
    val onChoice: (String, Int) -> Unit = { id, value -> viewModel.report { it.setBetaChoice(id, value) } }
    SettingsPage(title = SettingsStrings.FLOOR, onBack = onBack) {
        floorFeatureGroups(betaFeatures).forEach { group ->
            item(key = group.first().def.id) { FeatureCard(group, onToggle, onChoice) }
        }
    }
}

/** Reactive Obstacle Avoidance: the robot's obstacle rows in the official groups, one card each. */
@Composable
fun VacuumObstacleSettingsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val betaFeatures by viewModel.repository.betaFeatures.collectAsState()
    LoadFeatureRowsOnEntry(viewModel)
    val onToggle: (String, Boolean) -> Unit = { id, on -> viewModel.report { it.setBetaFeature(id, on) } }
    val onChoice: (String, Int) -> Unit = { id, value -> viewModel.report { it.setBetaChoice(id, value) } }
    SettingsPage(title = SettingsStrings.OBSTACLE, onBack = onBack) {
        obstacleFeatureGroups(betaFeatures).forEach { group ->
            item(key = group.first().def.id) { FeatureCard(group, onToggle, onChoice) }
        }
    }
}

/** A card of feature rows with dividers between them. */
@Composable
internal fun FeatureCard(
    rows: List<BetaFeatureState>,
    onToggle: (String, Boolean) -> Unit,
    onChoice: (String, Int) -> Unit = { _, _ -> },
) {
    SettingGroup {
        rows.forEachIndexed { i, f ->
            if (i > 0) RowDivider()
            FeatureToggleRow(f, onToggle, onChoice)
        }
    }
}
