package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.components.BonusBadge
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.strings.DeviceStrings

// ACTIVE is a normal job in progress (drying), shown in blue rather than as a problem.
internal enum class DockRowLevel { UNKNOWN, OK, WARN, ACTIVE }

/** One status row's text and level from dock_error_status; the official rows name the problem, else "Normal". */
internal fun dockRow(dockError: Int?, problems: Map<Int, String>): Pair<String, DockRowLevel> = when {
    dockError == null -> DeviceStrings.UNKNOWN to DockRowLevel.UNKNOWN
    dockError in problems -> problems.getValue(dockError) to DockRowLevel.WARN
    else -> DeviceStrings.NORMAL to DockRowLevel.OK
}

internal val CLEAN_TANK_PROBLEMS = mapOf(DockErrorCodes.WATER_EMPTY to DeviceStrings.OUT_OF_WATER)
internal val DIRTY_TANK_PROBLEMS = mapOf(
    DockErrorCodes.DIRTY_TANK_FULL to DeviceStrings.TANK_FULL,
    DockErrorCodes.DIRTY_TANK_LATCH_OPEN to DeviceStrings.LATCH_OPEN,
)
internal val DUST_BAG_PROBLEMS = mapOf(
    DockErrorCodes.NO_DUSTBIN to DeviceStrings.NOT_INSTALLED,
    DockErrorCodes.DUCT_BLOCKAGE to DeviceStrings.DUCT_BLOCKED,
)

/** Official "Dock Control" sheet: status rows, then Empty / Wash / Dry, each turning into its Stop while running. */
@Composable
internal fun DockSheetContent(
    status: VacuumStatus,
    busy: Boolean,
    onEmptyDustbin: () -> Unit,
    onStopEmptying: () -> Unit,
    onWashMop: () -> Unit,
    onStopWash: () -> Unit,
    onDryMop: () -> Unit,
    onStopDry: () -> Unit,
    onDockSettings: () -> Unit,
) {
    val waterAttached = status.waterBoxStatus == 1
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP
    val emptying = status.state == VacuumStateCodes.EMPTYING_BIN
    val washing = status.state == VacuumStateCodes.WASHING_MOP || status.state == VacuumStateCodes.WASHING_MOP_ALT

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(DeviceStrings.DOCK_CONTROL, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onDockSettings) {
                Icon(Icons.Default.MoreHoriz, contentDescription = DeviceStrings.DOCK_SETTINGS)
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DockStatusRow(DeviceStrings.CLEAN_WATER_TANK, dockRow(status.dockErrorStatus, CLEAN_TANK_PROBLEMS))
            DockStatusRow(DeviceStrings.DIRTY_WATER_TANK, dockRow(status.dockErrorStatus, DIRTY_TANK_PROBLEMS))
            DockStatusRow(DeviceStrings.DUST_BAG, dockRow(status.dockErrorStatus, DUST_BAG_PROBLEMS))
            if (drying) DockStatusRow(DeviceStrings.DRY, dryingText(status.remainingDryTimeSec) to DockRowLevel.ACTIVE)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (emptying) {
                DockActionButton(Modifier.weight(1f), Icons.Default.Stop, AppColors.Good, DeviceStrings.STOP_EMPTYING, !busy, beta = true, onClick = onStopEmptying)
            } else {
                DockActionButton(Modifier.weight(1f), Icons.Default.Delete, AppColors.Good, DeviceStrings.EMPTY, !busy, onClick = onEmptyDustbin)
            }
            if (washing) {
                DockActionButton(Modifier.weight(1f), Icons.Default.Stop, AppColors.Water, DeviceStrings.STOP, !busy, onClick = onStopWash)
            } else {
                DockActionButton(Modifier.weight(1f), Icons.Default.WaterDrop, AppColors.Water, DeviceStrings.WASH, !busy && waterAttached, onClick = onWashMop)
            }
            if (drying) {
                DockActionButton(Modifier.weight(1f), Icons.Default.Stop, AppColors.Warn, DeviceStrings.STOP_DRYING, !busy, onClick = onStopDry)
            } else {
                DockActionButton(Modifier.weight(1f), Icons.Default.Air, AppColors.Warn, DeviceStrings.DRY, !busy, onClick = onDryMop)
            }
        }
    }
}

@Composable
private fun DockStatusRow(label: String, value: Pair<String, DockRowLevel>) {
    val color = when (value.second) {
        DockRowLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        DockRowLevel.OK -> AppColors.Good
        DockRowLevel.WARN -> MaterialTheme.colorScheme.error
        DockRowLevel.ACTIVE -> AppColors.Water
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        // The label keeps its width; a long value wraps to the right of it instead of squeezing it.
        Text(label, style = MaterialTheme.typography.bodyMedium, softWrap = false)
        Text(
            value.first,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DockActionButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    enabled: Boolean,
    beta: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier, enabled = enabled, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = if (enabled) iconTint.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
            if (beta) BonusBadge(BonusKind.Beta)
        }
    }
}
