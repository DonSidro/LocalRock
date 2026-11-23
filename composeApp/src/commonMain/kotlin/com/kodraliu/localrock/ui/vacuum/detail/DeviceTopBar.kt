package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.ui.components.BonusBadge
import com.kodraliu.localrock.ui.components.BonusKind
import com.kodraliu.localrock.ui.strings.DeviceStrings

/** Official nav bar: back, name (or the running task), battery and status sub-title, then Camera / Map / Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceTopBar(
    deviceName: String,
    status: VacuumStatus,
    showCamera: Boolean = false,
    mapReady: Boolean,
    showPetSearch: Boolean,
    onBack: () -> Unit,
    onCamera: () -> Unit = {},
    onMap: () -> Unit,
    onSettings: () -> Unit,
    onPetSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    taskTitle(status) ?: deviceName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusSubtitle(status)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = DeviceStrings.BACK)
            }
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showCamera) RoundBarButton(Icons.Default.Videocam, DeviceStrings.REMOTE_VIEWING, true, onCamera)
                RoundBarButton(Icons.Default.Map, DeviceStrings.EDIT_MAP, mapReady, onMap)
                RoundBarButton(Icons.Default.Settings, DeviceStrings.SETTINGS, true, onSettings)
            }
            if (showPetSearch) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = DeviceStrings.MORE)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text(DeviceStrings.SEARCH_FOR_PET) },
                            leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null) },
                            trailingIcon = { BonusBadge(BonusKind.LocalRock) },
                            onClick = { open = false; onPetSearch() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RoundBarButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun StatusSubtitle(status: VacuumStatus) {
    val errorCode = status.errorCode ?: 0
    val charging = status.chargeStatus == 1 || status.state == VacuumStateCodes.CHARGING
    val battery = status.battery
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (battery != null) {
            Icon(
                batteryIcon(battery, charging),
                contentDescription = if (charging) DeviceStrings.BATTERY_CHARGING else DeviceStrings.BATTERY,
                modifier = Modifier.size(16.dp),
                tint = batteryColor(battery),
            )
            Text("$battery%", style = MaterialTheme.typography.labelMedium)
        }
        // The robot's own error replaces the state word, as the official sub-title does.
        val error = errorCode != 0
        Text(
            if (error) VacuumErrorCodes.describe(errorCode) else officialStatusText(status),
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun batteryIcon(battery: Int, charging: Boolean): ImageVector = when {
    charging -> Icons.Default.BatteryChargingFull
    battery >= 95 -> Icons.Default.BatteryFull
    battery >= 80 -> Icons.Default.Battery6Bar
    battery >= 65 -> Icons.Default.Battery5Bar
    battery >= 50 -> Icons.Default.Battery4Bar
    battery >= 35 -> Icons.Default.Battery3Bar
    battery >= 20 -> Icons.Default.Battery2Bar
    battery >= 0 -> Icons.Default.Battery1Bar
    else -> Icons.AutoMirrored.Filled.BatteryUnknown
}
