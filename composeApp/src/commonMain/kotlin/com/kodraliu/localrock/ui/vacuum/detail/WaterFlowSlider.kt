package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.WaterFlow
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.strings.DeviceStrings
import kotlin.math.roundToInt

private val THUMB_SIZE = 32.dp

/** The official 1-30 Water Flow slider: band word beside the title, level on the thumb, droplets under the track. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WaterFlowSlider(currentCode: Int?, enabled: Boolean, onLevelSelected: (Int) -> Unit) {
    val robotLevel = WaterFlow.levelOf(currentCode) ?: DEFAULT_WATER_FLOW_LEVEL
    var level by remember(robotLevel) { mutableFloatStateOf(robotLevel.toFloat()) }
    val shown = level.roundToInt()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DeviceStrings.WATER_FLOW, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(waterFlowBandLabel(WaterFlow.band(shown)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = level,
            onValueChange = { level = it },
            enabled = enabled,
            onValueChangeFinished = {
                val picked = level.roundToInt()
                if (picked != robotLevel) onLevelSelected(picked)
            },
            valueRange = WaterFlow.MIN_LEVEL.toFloat()..WaterFlow.MAX_LEVEL.toFloat(),
            steps = WaterFlow.MAX_LEVEL - WaterFlow.MIN_LEVEL - 1,
            colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            thumb = {
                Surface(Modifier.size(THUMB_SIZE), shape = CircleShape, color = MaterialTheme.colorScheme.primary, shadowElevation = 2.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("$shown", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            },
        )
        DropletMarks()
        if (WaterFlow.showsWarning(shown)) {
            Text(
                DeviceStrings.WATER_FLOW_WARNING,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Warn,
                textAlign = TextAlign.End,
            )
        }
    }
}

// One, two and three drops under levels 5, 15 and 25, lined up with the thumb's travel.
@Composable
private fun DropletMarks() {
    Layout(
        content = {
            WaterFlow.DROPLET_LEVELS.forEachIndexed { index, _ ->
                Row {
                    repeat(index + 1) {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = THUMB_SIZE / 2),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { index, placeable ->
                val fraction = (WaterFlow.DROPLET_LEVELS[index] - WaterFlow.MIN_LEVEL).toFloat() / (WaterFlow.MAX_LEVEL - WaterFlow.MIN_LEVEL)
                placeable.place((fraction * width - placeable.width / 2f).roundToInt(), 0)
            }
        }
    }
}
