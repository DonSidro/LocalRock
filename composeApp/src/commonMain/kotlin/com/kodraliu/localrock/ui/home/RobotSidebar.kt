package com.kodraliu.localrock.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.device.Reachability
import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.model.Product
import com.kodraliu.localrock.ui.AppColors

/**
 * Robot switcher shown as a persistent column on wide windows and as the drawer content on phones.
 * Rendered from the cheap device layer (name + reachability), so every robot's entry stays live
 * without opening a robot session.
 */
@Composable
fun RobotSidebar(
    devices: List<Device>,
    productsByDuid: Map<String, Product>,
    reachability: Map<String, Reachability>,
    selectedDuid: String?,
    onSelectRobot: (Device) -> Unit,
    onAddVacuum: (() -> Unit)?,
    onSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLogout by remember { mutableStateOf(false) }

    Column(modifier.fillMaxHeight().statusBarsPadding()) {
        Text(
            "My vacuums",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 12.dp),
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            devices.forEach { device ->
                RobotRow(
                    device = device,
                    model = productsByDuid[device.duid]?.model.orEmpty(),
                    reachability = reachability[device.duid],
                    selected = device.duid == selectedDuid,
                    onClick = { onSelectRobot(device) },
                )
            }
        }

        HorizontalDivider()
        FooterItem(Icons.Default.Add, "Add vacuum", enabled = onAddVacuum != null) {
            onAddVacuum?.invoke()
        }
        FooterItem(Icons.Default.Settings, "Settings", onClick = onSettings)
        FooterItem(Icons.AutoMirrored.Filled.Logout, "Sign out", onClick = { showLogout = true })
        Spacer(Modifier.height(8.dp))
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Sign out?") },
            text = { Text("You'll need to log in again with an email code to control your vacuums.") },
            confirmButton = {
                TextButton(onClick = { showLogout = false; onSignOut() }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RobotRow(
    device: Device,
    model: String,
    reachability: Reachability?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Without a local key there is nothing to talk to, so the row is disabled rather than
    // silently ignoring taps.
    val notPaired = device.localKey == null
    // The robot's own answer wins; the server flag is only used when it could not be asked.
    val online = when (reachability) {
        Reachability.ONLINE -> true
        Reachability.OFFLINE, Reachability.CHECKING -> false
        null -> device.online
    }
    val statusColor = when {
        notPaired -> MaterialTheme.colorScheme.error
        online -> AppColors.Good
        else -> MaterialTheme.colorScheme.outline
    }
    val statusText = when {
        notPaired -> "Setup incomplete"
        reachability == Reachability.CHECKING -> "Checking…"
        online -> "Online"
        else -> "Offline"
    }
    val nameColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val subColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        enabled = !notPaired,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = statusColor, modifier = Modifier.size(8.dp)) {}
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    if (model.isNotEmpty()) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = subColor)
                        Text(
                            model,
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FooterItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
