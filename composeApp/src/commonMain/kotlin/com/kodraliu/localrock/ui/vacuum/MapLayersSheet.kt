package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Display preferences only. No command or repository dependency belongs in this layer. */
internal data class MapLayerVisibility(
    val roomIcons: Boolean = true,
    val cleaningPath: Boolean = true,
    val restrictions: Boolean = true,
    val detectedObstacles: Boolean = true,
) {
    companion object {
        val Saver = listSaver<MapLayerVisibility, Boolean>(
            save = { listOf(it.roomIcons, it.cleaningPath, it.restrictions, it.detectedObstacles) },
            restore = { MapLayerVisibility(it[0], it[1], it[2], it[3]) },
        )
    }
}

@Composable
internal fun MapLayersSheet(map: ParsedMap, layers: MapLayerVisibility, onChange: (MapLayerVisibility) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Map layers", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
        Text("Choose what appears on the map. Cleaning settings stay the same.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        MapLayerSwitch("Room icons", layers.roomIcons) { onChange(layers.copy(roomIcons = it)) }
        MapLayerSwitch("Cleaning path", layers.cleaningPath) { onChange(layers.copy(cleaningPath = it)) }
        if (map.format == MapFormat.LEGACY) {
            MapLayerSwitch("Restrictions", layers.restrictions) { onChange(layers.copy(restrictions = it)) }
            MapLayerSwitch("Detected obstacles", layers.detectedObstacles) { onChange(layers.copy(detectedObstacles = it)) }
        }
    }
}

@Composable
private fun MapLayerSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}
