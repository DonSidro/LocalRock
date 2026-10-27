package com.kodraliu.localrock.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/*
 * In-app release notes. They ship inside the app, so nothing is fetched to show them. Add an
 * entry for each release that has something worth telling users; a version without one simply
 * shows nothing after the update.
 */

data class WhatsNewItem(val icon: ImageVector, val title: String, val body: String)

data class WhatsNewRelease(val version: String, val items: List<WhatsNewItem>)

/** Newest first. */
val WHATS_NEW: List<WhatsNewRelease> = listOf(
    WhatsNewRelease(
        version = "1.4.0",
        items = listOf(
            WhatsNewItem(
                Icons.Default.PlayArrow,
                "Routines",
                "More > Routines lists the routines your server keeps, the ones made in the " +
                    "Roborock app, and runs one with a tap.",
            ),
            WhatsNewItem(
                Icons.Default.PinDrop,
                "Zone clean and Pin & Go",
                "Both now send the spot you drew or tapped in the robot's own coordinates, so " +
                    "the robot goes where you meant.",
            ),
            WhatsNewItem(
                Icons.Default.Home,
                "Room types",
                "Pick a type such as Kitchen or Bedroom for a room, or give it a name of your own.",
            ),
            WhatsNewItem(
                Icons.Default.Schedule,
                "Schedules",
                "Set a schedule to any minute, shown in your phone's clock style. If the robot " +
                    "refuses a schedule, the app now says so.",
            ),
            WhatsNewItem(
                Icons.Default.Science,
                "Beta features",
                "On newer robots, Settings > Beta features switches options such as FlexiArm and " +
                    "gap deep clean. Only options your robot reports are shown.",
            ),
            WhatsNewItem(
                Icons.Default.Build,
                "Fixes",
                "A zoomed map now reaches the edge of the screen and keeps its zoom while the " +
                    "robot cleans. After a floor switch the right map appears once the robot " +
                    "has loaded it. Rooms can be renamed on robots with an empty room table, " +
                    "dock settings follow what each dock supports, the built-in water tank can " +
                    "be emptied through the dock, and the back button returns to the settings " +
                    "list. The sign-in screen is available in German.",
            ),
        ),
    ),
    WhatsNewRelease(
        version = "1.3.0",
        items = listOf(
            WhatsNewItem(
                Icons.Default.Tune,
                "Settings per room",
                "Choose Per room in the cleaning mode to give every room its own suction, water " +
                    "and route, for example mopping the kitchen and only vacuuming the bedroom.",
            ),
            WhatsNewItem(
                Icons.Default.Reorder,
                "Cleaning order",
                "Rooms you pick are cleaned in the order you tap them. More > Rooms > Default " +
                    "cleaning order sets the order for a clean without picked rooms.",
            ),
            WhatsNewItem(
                Icons.Default.AutoAwesome,
                "SmartPlan",
                "On robots that have it, SmartPlan lets the robot choose suction, water and " +
                    "route for each room.",
            ),
            WhatsNewItem(
                Icons.Default.Notifications,
                "Robot alerts",
                "Settings > Notifications can tell you when a robot gets stuck, finishes " +
                    "cleaning, or its dock needs attention.",
            ),
            WhatsNewItem(
                Icons.Default.Build,
                "Fixes",
                "The app connects faster when it starts and after the screen was off, and no " +
                    "longer shows a robot as online when it is not. Map editing and map " +
                    "switching work on older robots, and a robot that never used the official " +
                    "app can save its first map.",
            ),
        ),
    ),
    WhatsNewRelease(
        version = "1.2.0",
        items = listOf(
            WhatsNewItem(
                Icons.Default.Edit,
                "Map editor",
                "Tap the pencil on the map to draw no-go zones, invisible walls, carpets and " +
                    "thresholds, split and merge rooms, and set floor types.",
            ),
            WhatsNewItem(
                Icons.Default.Map,
                "Manage your maps",
                "More > Maps renames and deletes maps, and sends the robot out to map the home " +
                    "again, for example after moving the dock.",
            ),
            WhatsNewItem(
                Icons.Default.Layers,
                "Multi-level homes",
                "Keep a map for each floor, switch between them with the picker on the map, or " +
                    "let the robot recognise the floor by itself.",
            ),
            WhatsNewItem(
                Icons.Default.Build,
                "Fixes",
                "Mapping runs show as Mapping, round carpets keep their shape, and the cleaned " +
                    "area no longer flickers while the map changes.",
            ),
        ),
    ),
)

fun whatsNewFor(version: String): WhatsNewRelease? = WHATS_NEW.find { it.version == version }

/**
 * Show once after an update. Fresh installs never see it: the welcome screen marks the current
 * version as seen, so only someone who used an earlier version gets it.
 */
fun shouldShowWhatsNew(introSeen: Boolean, lastSeenVersion: String?, currentVersion: String): Boolean =
    introSeen && lastSeenVersion != currentVersion && whatsNewFor(currentVersion) != null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(release: WhatsNewRelease, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What's new", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "LocalRock ${release.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            release.items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Got it") }
        }
    }
}
