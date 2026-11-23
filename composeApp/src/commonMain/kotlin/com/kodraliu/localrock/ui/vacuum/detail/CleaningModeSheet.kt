package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.RoomCleanMode
import com.kodraliu.localrock.shared.vacuum.RouteGates
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.WaterFlow
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.isSmartModeSet
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.roomCleanModesFor
import com.kodraliu.localrock.shared.vacuum.routeOptions
import com.kodraliu.localrock.ui.strings.DeviceStrings

internal enum class CleanPlan(val label: String) {
    SMART(DeviceStrings.MODE_SMARTPLAN),
    MANUAL(DeviceStrings.MODE_GENERAL),
    PER_ROOM(DeviceStrings.MODE_CUSTOMIZE),
}

internal fun currentPlan(fanPower: Int?, waterMode: Int?, route: Int?): CleanPlan = when {
    isSmartModeSet(fanPower, waterMode, route) -> CleanPlan.SMART
    isCustomizeModeSet(fanPower, waterMode, route) -> CleanPlan.PER_ROOM
    else -> CleanPlan.MANUAL
}

/** Official mode sheet: SmartPlan / General / Customize header, General sub-tabs and rows, then "More cleaning settings". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleaningModeSheetContent(
    currentFanPower: Int?,
    currentWaterMode: Int?,
    currentRoute: Int?,
    seqType: Int?,
    cleaningCount: Int,
    busy: Boolean,
    supportsSmartPlan: Boolean,
    cleanThenMop: Boolean,
    pureMop: Boolean,
    waterSlide: Boolean,
    customWater: CustomWaterUi?,
    routeGates: RouteGates,
    rooms: List<ParsedMapRoom>,
    roomCleanModes: List<RoomCleanMode>,
    roomModesLoaded: Boolean?,
    error: String?,
    onClose: () -> Unit,
    onPlanSelected: (CleanPlan) -> Unit,
    onGeneralMode: (GeneralMode) -> Unit,
    onSaveRoomModes: (List<RoomCleanMode>) -> Unit,
    onFanPowerSelected: (Int) -> Unit,
    onWaterModeSelected: (Int) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
    onMoreSettings: () -> Unit,
) {
    val plan = currentPlan(currentFanPower, currentWaterMode, currentRoute)
    val plans = buildList {
        if (supportsSmartPlan || plan == CleanPlan.SMART) add(CleanPlan.SMART)
        add(CleanPlan.MANUAL)
        if (rooms.isNotEmpty() || plan == CleanPlan.PER_ROOM) add(CleanPlan.PER_ROOM)
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                plans.forEach { p ->
                    val active = p == plan
                    Surface(onClick = { if (!busy && !active) onPlanSelected(p) }, color = Color.Transparent, shape = MaterialTheme.shapes.small) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                p.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (active) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = DeviceStrings.CANCEL) }
        }
        error?.let {
            Text(it, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        when (plan) {
            CleanPlan.SMART -> Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(DeviceStrings.TIP_SMART, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CountSelector(cleaningCount, onCountChange)
            }
            CleanPlan.PER_ROOM -> when (roomModesLoaded) {
                true -> RoomCleanModesEditor(rooms, roomCleanModes, busy, routeGates, onSaveRoomModes)
                else -> Text(
                    if (roomModesLoaded == null) DeviceStrings.ROOM_MODES_LOADING else DeviceStrings.ROOM_MODES_FAILED,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CleanPlan.MANUAL -> GeneralPanel(
                currentFanPower, currentWaterMode, currentRoute, seqType, cleaningCount, busy, cleanThenMop, pureMop,
                waterSlide, customWater, routeGates, onGeneralMode, onFanPowerSelected, onWaterModeSelected, onRouteSelected, onCountChange,
            )
        }

        Surface(
            onClick = onMoreSettings,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DeviceStrings.MORE_CLEANING_SETTINGS, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneralPanel(
    currentFanPower: Int?,
    currentWaterMode: Int?,
    currentRoute: Int?,
    seqType: Int?,
    cleaningCount: Int,
    busy: Boolean,
    cleanThenMop: Boolean,
    pureMop: Boolean,
    waterSlide: Boolean,
    customWater: CustomWaterUi?,
    routeGates: RouteGates,
    onGeneralMode: (GeneralMode) -> Unit,
    onFanPowerSelected: (Int) -> Unit,
    onWaterModeSelected: (Int) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onCountChange: (Int) -> Unit,
) {
    val robotMode = currentGeneralMode(currentFanPower, currentWaterMode, seqType)
    val modes = generalModes(cleanThenMop, pureMop, robotMode == GeneralMode.MOP)
    // Follows the tap at once; the robot's answer arrives with the next status.
    var shown by remember(robotMode) { mutableStateOf(robotMode) }
    SecondaryScrollableTabRow(selectedTabIndex = modes.indexOf(shown).coerceAtLeast(0), edgePadding = 16.dp) {
        modes.forEach { mode ->
            Tab(
                selected = shown == mode,
                onClick = {
                    if (!busy && mode != shown) {
                        shown = mode
                        onGeneralMode(mode)
                    }
                },
                text = { Text(generalModeLabel(mode)) },
            )
        }
    }
    // Every tab is measured and the sheet keeps the tallest height, so the tab row never moves under a finger.
    TallestOf(
        variants = modes,
        shown = shown,
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    ) { mode ->
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(generalModeTip(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mode != GeneralMode.MOP) {
                ModeOptionSelector(DeviceStrings.SUCTION_POWER, FAN_OPTIONS.map { it.second }, FAN_OPTIONS.indexOfFirst { it.first == currentFanPower }, !busy) {
                    onFanPowerSelected(FAN_OPTIONS[it].first)
                }
            }
            if (mode != GeneralMode.VACUUM) {
                if (waterSlide) {
                    WaterFlowSlider(currentWaterMode, !busy) { onWaterModeSelected(WaterFlow.codeFor(it)) }
                } else if (customWater != null) {
                    CustomWaterFlowSelector(currentWaterMode, customWater, !busy, onWaterModeSelected)
                } else {
                    ModeOptionSelector(DeviceStrings.WATER_FLOW, WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(currentWaterMode) }, !busy) {
                        onWaterModeSelected(WATER_OPTIONS[it].first)
                    }
                }
            }
            CountSelector(cleaningCount, onCountChange)
            val routes = routeOptions(routeGates, mopOnly = mode == GeneralMode.MOP)
            ModeOptionSelector(DeviceStrings.ROUTE, routes.map { routeLabel(it.name) }, routes.indexOfFirst { it.code == currentRoute }, !busy) {
                onRouteSelected(routes[it].code)
            }
        }
    }
}

/** Measures every variant but places only [shown], at the tallest variant's height. Unplaced ones draw nothing and take no taps. */
@Composable
private fun <T> TallestOf(variants: List<T>, shown: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val all = if (shown in variants) variants else variants + shown
    Layout(content = { all.forEach { content(it) } }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val visible = placeables[all.indexOf(shown)]
        val height = placeables.maxOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.constrainWidth(placeables.maxOf { it.width }), height) { visible.place(0, 0) }
    }
}

private fun roomModeSummary(mode: RoomCleanMode): String {
    val fan = ROOM_FAN_OPTIONS.firstOrNull { it.first == mode.fanPower }?.second ?: DeviceStrings.OTHER
    val water = waterLabel(mode.waterBoxMode) ?: DeviceStrings.OTHER
    return when {
        mode.waterBoxMode == WaterBoxMode.OFF -> "${DeviceStrings.MODE_VACUUM} $fan"
        mode.fanPower == VacuumFanPower.OFF -> "${DeviceStrings.MODE_MOP} $water"
        else -> "$fan, $water"
    }
}

/** Customize: one row per room; a tapped room opens its suction, water and route. Nothing is sent until Save. */
@Composable
private fun RoomCleanModesEditor(
    rooms: List<ParsedMapRoom>,
    stored: List<RoomCleanMode>,
    busy: Boolean,
    routeGates: RouteGates,
    onSave: (List<RoomCleanMode>) -> Unit,
) {
    val saved = remember(rooms, stored) { roomCleanModesFor(rooms.map { it.id }, stored) }
    var draft by remember(saved) { mutableStateOf(saved) }
    var openSegment by remember { mutableStateOf<Int?>(null) }
    // Rooms the robot has no settings for yet still need the first save.
    val unsaved = draft != saved || rooms.any { room -> stored.none { it.segment == room.id } }

    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (rooms.isEmpty()) DeviceStrings.ROOM_MODES_NO_MAP else DeviceStrings.TIP_CUSTOMIZE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            rooms.forEachIndexed { index, room ->
                val mode = draft.firstOrNull { it.segment == room.id } ?: return@forEachIndexed
                val open = openSegment == room.id
                val update = { changed: RoomCleanMode ->
                    draft = draft.map { if (it.segment == room.id) changed else it }
                }
                if (index > 0) HorizontalDivider()
                Surface(onClick = { openSegment = if (open) null else room.id }, color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            room.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(roomModeSummary(mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (open) {
                    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ModeOptionSelector(DeviceStrings.SUCTION_POWER, ROOM_FAN_OPTIONS.map { it.second }, ROOM_FAN_OPTIONS.indexOfFirst { it.first == mode.fanPower }, !busy) {
                            val fan = ROOM_FAN_OPTIONS[it].first
                            // A room with neither suction nor water would not be cleaned at all.
                            val water = if (fan == VacuumFanPower.OFF && mode.waterBoxMode == WaterBoxMode.OFF) WaterBoxMode.LOW else mode.waterBoxMode
                            // Deep routes some robots allow only without suction fall back to Standard.
                            val offered = routeOptions(routeGates, mopOnly = fan == VacuumFanPower.OFF).map { it.code }
                            val route = if (mode.mopMode in DEEP_ROUTES && mode.mopMode !in offered) MopRoute.STANDARD else mode.mopMode
                            update(mode.copy(fanPower = fan, waterBoxMode = water, mopMode = route))
                        }
                        ModeOptionSelector(DeviceStrings.WATER_FLOW, WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == WaterBoxMode.normalize(mode.waterBoxMode) }, !busy) {
                            val water = WATER_OPTIONS[it].first
                            val fan = if (water == WaterBoxMode.OFF && mode.fanPower == VacuumFanPower.OFF) VacuumFanPower.BALANCED else mode.fanPower
                            // The route is hidden without water. A Qrevo S Pro (02.33.88) refuses Deep and Deep+ in a
                            // room that vacuums and a Qrevo Curv 2 Flow refuses Deep in any room, so a hidden one
                            // would make the whole save fail.
                            val route = if (water == WaterBoxMode.OFF && mode.mopMode in DEEP_ROUTES) MopRoute.STANDARD else mode.mopMode
                            update(mode.copy(fanPower = fan, waterBoxMode = water, mopMode = route))
                        }
                        if (mode.waterBoxMode != WaterBoxMode.OFF) {
                            val routes = routeOptions(routeGates, mopOnly = mode.fanPower == VacuumFanPower.OFF)
                            ModeOptionSelector(DeviceStrings.ROUTE, routes.map { routeLabel(it.name) }, routes.indexOfFirst { it.code == mode.mopMode }, !busy) {
                                update(mode.copy(mopMode = routes[it].code))
                            }
                        }
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            Button(onClick = { onSave(draft) }, enabled = !busy && unsaved, modifier = Modifier.fillMaxWidth()) {
                Text(DeviceStrings.SAVE_ROOM_MODES)
            }
        }
    }
}

@Composable
private fun ModeOptionSelector(label: String, options: List<String>, selectedIndex: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    val currentValue = options.getOrElse(selectedIndex) { "" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(currentValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val selected = selectedIndex == index
                Surface(
                    onClick = { if (enabled) onSelect(index) },
                    modifier = Modifier.weight(1f),
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(option, modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CountSelector(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(DeviceStrings.CLEANING_COUNT, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(DeviceStrings.fill(DeviceStrings.CLEAN_COUNT_TIMES, selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 2).forEach { count ->
                val isSelected = selected == count
                Surface(onClick = { onSelect(count) }, modifier = Modifier.weight(1f), shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text("×$count", modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}
