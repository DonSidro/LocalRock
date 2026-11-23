package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.vacuum.MainTab
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.ui.strings.DeviceStrings

/** Whether the round button can start anything from [tab]; pause and resume always can. */
internal fun startEnabled(action: StartAction, tab: MainTab, selectedRooms: Int): Boolean = when {
    action != StartAction.CLEAN -> true
    tab == MainTab.ROOM -> selectedRooms > 0
    tab == MainTab.ROUTINE -> false
    else -> true
}

/** Bottom panel: Full / Room / Zone / Routine tabs, the tab's content, then mode chip, round Start and Dock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ControlPanel(
    tabs: List<MainTab>,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    status: VacuumStatus,
    busy: Boolean,
    modeLabel: String,
    onModeChip: () -> Unit,
    rooms: List<ParsedMapRoom>,
    selectedRoomIds: List<Int>,
    onRoomToggle: (Int) -> Unit,
    onDefaultOrder: () -> Unit,
    onOpenZones: () -> Unit,
    routines: RoutinesState,
    onRunRoutine: (Scene) -> Unit,
    onRetryRoutines: () -> Unit,
    onStart: (StartAction) -> Unit,
    onStop: () -> Unit,
    onDock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = startAction(status.state, status.inCleaning)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { t ->
                    Tab(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        text = { Text(tabLabel(t), fontWeight = if (t == tab) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }

            Box(Modifier.padding(horizontal = 16.dp)) {
                when (tab) {
                    MainTab.FULL -> Unit
                    MainTab.ROOM -> RoomTabContent(rooms, selectedRoomIds, busy, onRoomToggle, onDefaultOrder)
                    MainTab.ZONE -> ZoneTabContent(busy, onOpenZones)
                    MainTab.ROUTINE -> RoutineTabContent(routines, busy, onRunRoutine, onRetryRoutines)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(Modifier.weight(1f), Icons.Default.Tune, modeLabel, enabled = !busy, onClick = onModeChip)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Stop has no place on the official bar; a small visible button while a job is open beats a hidden long-press.
                    val taskOpen = action != StartAction.CLEAN
                    if (taskOpen) Spacer(Modifier.width(44.dp))
                    StartButton(action, status.inCleaning, enabled = !busy && startEnabled(action, tab, selectedRoomIds.size)) { onStart(action) }
                    if (taskOpen) {
                        FilledTonalIconButton(onClick = onStop, enabled = !busy, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Stop, contentDescription = DeviceStrings.END_CLEAN, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                BarButton(Modifier.weight(1f), Icons.Default.Home, dockLabel(status.state, status.inCleaning), enabled = !busy, onClick = onDock)
            }
        }
    }
}

@Composable
private fun StartButton(action: StartAction, inCleaning: Int?, enabled: Boolean, onClick: () -> Unit) {
    val label = startLabel(action, inCleaning)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(72.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                if (action == StartAction.PAUSE) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun BarButton(modifier: Modifier, icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoomTabContent(
    rooms: List<ParsedMapRoom>,
    selectedRoomIds: List<Int>,
    busy: Boolean,
    onRoomToggle: (Int) -> Unit,
    onDefaultOrder: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (rooms.isEmpty()) DeviceStrings.NO_ROOMS else roomTip(selectedRoomIds.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rooms.isNotEmpty()) {
            // Rooms are tapped on the map; the chips repeat them for screen readers and small maps.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { room ->
                    val order = selectedRoomIds.indexOf(room.id).takeIf { it >= 0 }?.plus(1)
                    FilterChip(
                        selected = order != null,
                        onClick = { onRoomToggle(room.id) },
                        enabled = !busy,
                        label = { Text(if (order != null) "$order  ${room.name}" else room.name, maxLines = 1) },
                    )
                }
            }
            TextButton(onClick = onDefaultOrder, enabled = !busy, modifier = Modifier.height(32.dp)) {
                Text(DeviceStrings.DEFAULT_ORDER, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ZoneTabContent(busy: Boolean, onOpenZones: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            DeviceStrings.ZONE_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onOpenZones, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(DeviceStrings.TIP_ADD_ZONE)
        }
    }
}
