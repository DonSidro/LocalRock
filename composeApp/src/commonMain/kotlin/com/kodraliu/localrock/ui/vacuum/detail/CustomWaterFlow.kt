package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CustomWater
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.ui.strings.DeviceStrings
import kotlin.math.roundToInt

/** What the sheet needs for the Custom water slider; null on robots without it. */
internal class CustomWaterUi(val offset: Int, val distance: Int?, val onDistance: (Int) -> Unit)

/** "Custom | Moderate" for the robot's custom amount, or just "Custom" when the amount is unknown. */
internal fun customWaterLabel(distance: Int?, offset: Int): String {
    val band = distance?.let { CustomWater.band(it, offset) } ?: return DeviceStrings.WATER_CUSTOM
    return "${DeviceStrings.WATER_CUSTOM} | ${waterFlowBandLabel(band)}"
}

/**
 * The official Water Flow row on robots with the Custom slider: preset chips plus a Custom chip. Tapping
 * Custom switches the robot to mode 207 and opens a 1-30 slider; the check button sends the amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomWaterFlowSelector(currentCode: Int?, custom: CustomWaterUi, enabled: Boolean, onWaterModeSelected: (Int) -> Unit) {
    val isCustom = currentCode == CustomWater.MODE
    val robotLevel = CustomWater.levelOf(custom.distance, custom.offset) ?: DEFAULT_WATER_FLOW_LEVEL
    var editing by remember { mutableStateOf(false) }
    var level by remember(robotLevel) { mutableFloatStateOf(robotLevel.toFloat()) }
    val shownLevel = level.roundToInt()
    val selectedPreset = WATER_OPTIONS.indexOfFirst { !isCustom && it.first == WaterBoxMode.normalize(currentCode) }
    val label = when {
        editing -> customWaterLabel(CustomWater.distanceFor(shownLevel, custom.offset), custom.offset)
        isCustom -> customWaterLabel(custom.distance, custom.offset)
        else -> WATER_OPTIONS.getOrNull(selectedPreset)?.second.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DeviceStrings.WATER_FLOW, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                Slider(
                    value = level,
                    onValueChange = { level = it },
                    enabled = enabled,
                    valueRange = CustomWater.MIN_LEVEL.toFloat()..CustomWater.MAX_LEVEL.toFloat(),
                    steps = CustomWater.MAX_LEVEL - CustomWater.MIN_LEVEL - 1,
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                    thumb = {
                        Surface(Modifier.size(32.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 2.dp) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("$shownLevel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    },
                    modifier = Modifier.weight(3f),
                )
                WaterChip(selected = true, enabled = enabled, modifier = Modifier.weight(1f), onClick = {
                    editing = false
                    val distance = CustomWater.distanceFor(shownLevel, custom.offset)
                    if (distance != custom.distance || !isCustom) custom.onDistance(distance)
                }) { Icon(Icons.Default.Check, DeviceStrings.WATER_CUSTOM_CONFIRM) }
            } else {
                WATER_OPTIONS.forEachIndexed { index, (code, name) ->
                    WaterChip(selected = index == selectedPreset, enabled = enabled, modifier = Modifier.weight(1f), onClick = { onWaterModeSelected(code) }) {
                        Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            fontWeight = if (index == selectedPreset) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                WaterChip(selected = isCustom, enabled = enabled, modifier = Modifier.weight(1f), onClick = {
                    if (!isCustom) onWaterModeSelected(CustomWater.MODE)
                    editing = true
                }) { Icon(Icons.Default.Edit, DeviceStrings.WATER_CUSTOM) }
            }
        }
    }
}

@Composable
private fun WaterChip(selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) { content() }
    }
}
