package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.settings.AppSettings
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.strings.MapStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Map Details switches show on the map; all on by default. */
data class MapDisplay(
    val roomNames: Boolean = true,
    val floorType: Boolean = true,
    val furniture: Boolean = true,
    val obstacle: Boolean = true,
    val homeLayout: Boolean = true,
) {
    fun encode(): Int =
        (if (roomNames) ROOM_NAMES else 0) or (if (floorType) FLOOR_TYPE else 0) or (if (furniture) FURNITURE else 0) or
            (if (obstacle) OBSTACLE else 0) or (if (homeLayout) HOME_LAYOUT else 0)

    companion object {
        private const val ROOM_NAMES = 1
        private const val FLOOR_TYPE = 2
        private const val FURNITURE = 4
        private const val OBSTACLE = 8
        private const val HOME_LAYOUT = 16

        val Default = MapDisplay()

        fun decode(flags: Int?): MapDisplay = if (flags == null) Default else MapDisplay(
            roomNames = flags and ROOM_NAMES != 0,
            floorType = flags and FLOOR_TYPE != 0,
            furniture = flags and FURNITURE != 0,
            obstacle = flags and OBSTACLE != 0,
            homeLayout = flags and HOME_LAYOUT != 0,
        )
    }
}

/** One robot's Map Details choice, shared by every screen that draws its map and kept in the app settings. */
class MapDisplayPrefs internal constructor(private val settings: AppSettings, private val duid: String) {
    private val _display = MutableStateFlow(MapDisplay.decode(settings.mapDetails(duid)))
    val display: StateFlow<MapDisplay> = _display.asStateFlow()

    fun set(value: MapDisplay) {
        settings.setMapDetails(duid, value.encode())
        _display.value = value
    }

    companion object {
        private val byDevice = HashMap<String, MapDisplayPrefs>()

        fun forDevice(settings: AppSettings, duid: String): MapDisplayPrefs =
            byDevice.getOrPut(duid) { MapDisplayPrefs(settings, duid) }
    }
}

@Composable
fun rememberMapDisplayPrefs(viewModel: VacuumViewModel): MapDisplayPrefs {
    val settings = LocalAppContainer.current.appSettings
    val duid = viewModel.repository.duid
    return remember(settings, duid) { MapDisplayPrefs.forDevice(settings, duid) }
}

/** The robot's Map Details choice for drawing its map; the device page passes it to the map renderer. */
@Composable
fun rememberMapDisplay(viewModel: VacuumViewModel): State<MapDisplay> = rememberMapDisplayPrefs(viewModel).display.collectAsState()

/** The floor plan switch is the official "Home Layout" only on robots and maps that have one (bit 122, block 55). */
internal fun hasHomeLayout(floorPlanBit: Boolean, map: ParsedMap?): Boolean =
    floorPlanBit && map != null && map.zoneLines.isNotEmpty()

private data class DetailToggle(val label: String, val icon: ImageVector, val on: Boolean, val enabled: Boolean, val flip: (MapDisplay) -> MapDisplay)

/** The toggle tiles, four per row, as the official Map Details sheet lays them out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MapDetailsContent(display: MapDisplay, showHomeLayout: Boolean, onChange: (MapDisplay) -> Unit, modifier: Modifier = Modifier) {
    val toggles = buildList {
        add(DetailToggle(MapStrings.DETAIL_ROOM_NAME, Icons.AutoMirrored.Filled.Label, display.roomNames, true) { it.copy(roomNames = !it.roomNames) })
        add(DetailToggle(MapStrings.DETAIL_FLOOR_TYPE, Icons.Default.Texture, display.floorType, true) { it.copy(floorType = !it.floorType) })
        // LocalRock does not draw furniture yet, so the switch has nothing to show or hide.
        add(DetailToggle(MapStrings.DETAIL_FURNITURE, Icons.Default.Chair, display.furniture, false) { it.copy(furniture = !it.furniture) })
        add(DetailToggle(MapStrings.DETAIL_OBSTACLE, Icons.Default.ReportProblem, display.obstacle, true) { it.copy(obstacle = !it.obstacle) })
        if (showHomeLayout) {
            add(DetailToggle(MapStrings.DETAIL_HOME_LAYOUT, Icons.Default.Dashboard, display.homeLayout, true) { it.copy(homeLayout = !it.homeLayout) })
        }
    }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        maxItemsInEachRow = 4,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        toggles.forEach { t -> DetailTile(t, onClick = { onChange(t.flip(display)) }, modifier = Modifier.weight(1f)) }
        // Keeps a short last row aligned to the grid instead of spreading out.
        repeat((4 - toggles.size % 4) % 4) { Box(Modifier.weight(1f)) }
    }
}

@Composable
private fun DetailTile(t: DetailToggle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = t.enabled, role = Role.Switch, onClick = onClick)
            .alpha(if (t.enabled) 1f else 0.38f)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (t.on) scheme.primaryContainer else scheme.surfaceContainerHighest,
            border = if (t.on) BorderStroke(2.dp, scheme.primary) else null,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(t.icon, contentDescription = null, tint = if (t.on) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
            }
        }
        Text(t.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** Sheet body: title with a close button, then the tiles. */
@Composable
internal fun MapDetailsSheet(viewModel: VacuumViewModel, onClose: () -> Unit) {
    val prefs = rememberMapDisplayPrefs(viewModel)
    val display by prefs.display.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val map by viewModel.repository.parsedMap.collectAsState()
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MapStrings.MAP_DETAILS, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, MapStrings.CLOSE) }
        }
        MapDetailsContent(display, hasHomeLayout(caps.advertised.floorPlanMap, map), prefs::set, Modifier.padding(top = 8.dp))
    }
}

/** The VacuumMapDetails route as a page, for entries that open it outside Manage Maps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDetailsScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val prefs = rememberMapDisplayPrefs(viewModel)
    val display by prefs.display.collectAsState()
    val caps by viewModel.capabilities.collectAsState()
    val map by viewModel.repository.parsedMap.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(MapStrings.MAP_DETAILS) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            MapDetailsContent(display, hasHomeLayout(caps.advertised.floorPlanMap, map), prefs::set)
        }
    }
}
