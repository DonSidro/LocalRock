package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.FloorMap
import com.kodraliu.localrock.shared.vacuum.mapNameBlocker
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import kotlin.coroutines.cancellation.CancellationException
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import androidx.compose.foundation.layout.PaddingValues

@Composable
internal fun MapsSheetContent(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    loading: Boolean,
    busy: Boolean,
    blockedReason: String?,
    canAddMap: Boolean,
    unsavedMap: Boolean,
    mapSaving: Boolean?,
    multiLevel: Boolean?,
    smartSwitching: Boolean?,
    onMultiLevelChange: (Boolean) -> Unit,
    onSmartSwitchingChange: (Boolean) -> Unit,
    onEnableMapSaving: () -> Unit,
    onLoad: (Int) -> Unit,
    onRename: (FloorMap) -> Unit,
    onDelete: (FloorMap) -> Unit,
    onBackup: (FloorMap, picture: ParsedMap?) -> Unit,
    onRestore: (FloorMap, backupPicture: ParsedMap?) -> Unit,
    loadSavedMap: suspend (mapFlag: Int, backup: Boolean) -> ParsedMap,
    onStartMapping: () -> Unit,
    onSaveNewMap: () -> Unit,
    onRecheck: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Maps", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The maps stored on your robot. The robot keeps one backup of each map: back it up while " +
                "the map looks right, and restore it if the map goes wrong. Moved the dock somewhere the " +
                "map doesn't cover? Delete the map, then let the robot map the home again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (blockedReason != null) {
            Text(blockedReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        val canManage = !busy && !loading && blockedReason == null

        // A picture of each saved map and its backup, fetched when the sheet opens and again for a
        // map or backup the robot reports as changed.
        val previews = remember { mutableStateMapOf<String, ParsedMap>() }
        val failedPreviews = remember { mutableStateMapOf<String, Boolean>() }
        LaunchedEffect(floorMaps) {
            for (floor in floorMaps) {
                for (backup in listOf(false, true)) {
                    if (backup && floor.backupAt == null) continue
                    val key = previewKey(floor, backup)
                    if (key in previews) continue
                    failedPreviews.remove(key)
                    try {
                        previews[key] = loadSavedMap(floor.mapFlag, backup)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedPreviews[key] = true
                    }
                }
            }
        }

        // A robot that holds saved maps keeps maps, whatever its status says.
        if (mapSaving == false && floorMaps.isEmpty() && !loading) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map saving is off", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "This robot does not keep a map yet, so there are no rooms, zones or floors to work with. " +
                            "Turn map saving on to let it store one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onEnableMapSaving, enabled = canManage, modifier = Modifier.fillMaxWidth()) {
                        Text("Turn on map saving")
                    }
                }
            }
        }

        if (multiLevel != null) {
            SettingRowSwitch(
                title = "Multi-level maps",
                subtitle = "Keep up to one map per floor. Turning this off keeps one map and deletes the rest.",
                checked = multiLevel,
                enabled = canManage,
                padding = PaddingValues(0.dp),
                onCheckedChange = onMultiLevelChange,
            )
            if (multiLevel && smartSwitching != null) {
                SettingRowSwitch(
                    title = "Recognise the floor automatically",
                    subtitle = "Off: you pick the map before cleaning another floor.",
                    checked = smartSwitching,
                    enabled = canManage,
                    padding = PaddingValues(0.dp),
                    onCheckedChange = onSmartSwitchingChange,
                )
            }
            HorizontalDivider()
        }

        if (unsavedMap) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New map not saved", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "The robot has built a new map. Stop the mapping run when it has covered the home, " +
                            "then save it so it is kept and split into rooms.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onSaveNewMap, enabled = canManage, modifier = Modifier.fillMaxWidth()) {
                        Text("Save new map")
                    }
                }
            }
        }

        when {
            loading && floorMaps.isEmpty() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Checking robot for saved maps…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            floorMaps.isEmpty() -> {
                Text("No saved maps on the robot.", style = MaterialTheme.typography.titleSmall)
                Text(
                    "The robot leaves the dock and drives through the home to build a new map. Stop it once " +
                        "it has covered everything, then save the map here. Open the doors to every room you want on it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onStartMapping, enabled = canManage && !unsavedMap, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Map the home")
                }
                OutlinedButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }

            else -> {
                floorMaps.forEach { floor ->
                    val selected = floor.mapFlag == currentFloorFlag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(floor.name, style = MaterialTheme.typography.bodyLarge)
                            if (selected) {
                                Text("Currently loaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                floor.backupAt?.let { "Backup from ${formatCleanRecordTime(it)}" } ?: "No backup yet",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRename(floor) }, enabled = canManage) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename ${floor.name}")
                        }
                        IconButton(onClick = { onDelete(floor) }, enabled = canManage) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete ${floor.name}",
                                tint = if (canManage) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            )
                        }
                        // Loading only matters when there is another map to switch to.
                        if (floorMaps.size > 1) {
                            Button(onClick = { onLoad(floor.mapFlag) }, enabled = !busy && !selected) {
                                Text(if (selected) "Loaded" else "Load")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SavedMapPreview(
                            label = "Map",
                            map = previews[previewKey(floor, backup = false)],
                            failed = failedPreviews[previewKey(floor, backup = false)] == true,
                            modifier = Modifier.weight(1f),
                        )
                        if (floor.backupAt != null) {
                            SavedMapPreview(
                                label = "Backup",
                                map = previews[previewKey(floor, backup = true)],
                                failed = failedPreviews[previewKey(floor, backup = true)] == true,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        OutlinedButton(onClick = { onBackup(floor, previews[previewKey(floor, backup = false)]) }, enabled = canManage) {
                            Text("Back up now")
                        }
                        // Restoring was only captured for the loaded map.
                        OutlinedButton(onClick = { onRestore(floor, previews[previewKey(floor, backup = true)]) }, enabled = canManage && selected && floor.backupAt != null) {
                            Text("Restore backup")
                        }
                    }
                    HorizontalDivider()
                }
                if (canAddMap) {
                    OutlinedButton(onClick = onStartMapping, enabled = canManage && !unsavedMap, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Map, null); Spacer(Modifier.width(8.dp)); Text("Map another floor")
                    }
                }
                TextButton(onClick = onRecheck, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-check")
                }
            }
        }
    }
}

@Composable
internal fun RenameMapDialog(floor: FloorMap, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(floor) { mutableStateOf(floor.name) }
    val problem = mapNameBlocker(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename map") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Map name") },
                isError = problem != null && name.isNotEmpty(),
                supportingText = { if (problem != null && name.isNotEmpty()) Text(problem) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = problem == null && name.trim() != floor.name) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Turning multi-level maps off keeps one map; the robot deletes the others (captured). */
@Composable
internal fun KeepMapDialog(
    floorMaps: List<FloorMap>,
    currentFloorFlag: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var keep by remember(floorMaps) {
        mutableStateOf(floorMaps.find { it.mapFlag == currentFloorFlag }?.mapFlag ?: floorMaps.first().mapFlag)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep which map?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Single-level mode keeps one map. The robot deletes the others with their rooms " +
                        "and zones. This can't be undone.",
                )
                floorMaps.forEach { floor ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = keep == floor.mapFlag, onClick = { keep = floor.mapFlag })
                        Text(floor.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(keep) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Keep this map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Changes when the robot stores the map or its backup again, so a new picture is fetched. */
private fun previewKey(floor: FloorMap, backup: Boolean): String =
    if (backup) "${floor.mapFlag}:backup:${floor.backupAt}" else "${floor.mapFlag}:map:${floor.savedAt}"

/** A small picture of a saved map or its backup in the map list. */
@Composable
private fun SavedMapPreview(label: String, map: ParsedMap?, failed: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            when {
                map != null -> SavedMapImage(map, Modifier.fillMaxSize().padding(6.dp))
                failed -> Text("Couldn't load", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/** The floor plan of [map], cut to the part that holds floor and fitted into the space. */
@Composable
internal fun SavedMapImage(map: ParsedMap, modifier: Modifier = Modifier, rotationDegrees: Int = 0) {
    val bitmap = rememberMapBitmap(map)
    val bounds = remember(map) { map.contentBoundsNorm() }
    Canvas(modifier) {
        val usable = bounds.isUsable
        val srcLeft = if (usable) bounds.left * bitmap.width else 0f
        val srcTop = if (usable) bounds.top * bitmap.height else 0f
        val srcW = (if (usable) bounds.width * bitmap.width else bitmap.width.toFloat()).coerceAtLeast(1f)
        val srcH = (if (usable) bounds.height * bitmap.height else bitmap.height.toFloat()).coerceAtLeast(1f)
        val sideways = rotationDegrees % 180 != 0
        val scale = minOf(size.width / (if (sideways) srcH else srcW), size.height / (if (sideways) srcW else srcH))
        val dstW = srcW * scale
        val dstH = srcH * scale
        rotate(rotationDegrees.toFloat()) {
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                srcSize = IntSize(srcW.toInt(), srcH.toInt()),
                dstOffset = IntOffset(((size.width - dstW) / 2).toInt(), ((size.height - dstH) / 2).toInt()),
                dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

@Composable
internal fun BackupMapDialog(floor: FloorMap, picture: ParsedMap?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Back up \"${floor.name}\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The map as the robot will save it; absent while the sheet is still fetching it.
                picture?.let { DialogMapPicture(it) }
                Text(
                    "The robot saves this map as it is now, with its rooms, zones, walls and carpets. " +
                        (floor.backupAt?.let { "It keeps one backup per map, so this replaces the backup from ${formatCleanRecordTime(it)}." }
                            ?: "It keeps one backup per map."),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Back up") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun RestoreBackupDialog(floor: FloorMap, picture: ParsedMap?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore \"${floor.name}\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The backup the map goes back to; absent while the sheet is still fetching it.
                picture?.let { DialogMapPicture(it) }
                Text(
                    "The map goes back to the backup from ${floor.backupAt?.let { formatCleanRecordTime(it) } ?: "earlier"}. " +
                        "Everything changed on it since then is lost: rooms, room names, no-go zones, walls, carpets " +
                        "and thresholds. Check your routines afterwards.",
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A saved map's picture at the top of a backup or restore dialog. */
@Composable
private fun DialogMapPicture(map: ParsedMap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        SavedMapImage(map, Modifier.fillMaxSize().padding(8.dp))
    }
}

@Composable
internal fun DeleteMapDialog(floor: FloorMap, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete \"${floor.name}\"?") },
        text = {
            Text(
                "The robot forgets this map and everything on it: rooms, room names, floor types, " +
                    "no-go zones, walls, carpets and thresholds. This can't be undone.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete map") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
