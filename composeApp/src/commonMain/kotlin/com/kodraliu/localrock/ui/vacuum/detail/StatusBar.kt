package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.ui.strings.DeviceStrings

/** Compact area and time line under the map; battery and status moved to the top bar. Tapping opens History. */
@Composable
internal fun StatusBar(
    status: VacuumStatus,
    lastCleanArea: Long?,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drying = status.dryStatus == 1 || status.state == VacuumStateCodes.DRYING_MOP
    val detail = cleanDetailLine(lastCleanArea, status.cleanTime, status.state in ACTIVE_CLEANING_STATES)
    Surface(
        onClick = onHistory,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = DeviceStrings.HISTORY,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (drying) {
                    Text(
                        dryingText(status.remainingDryTimeSec),
                        style = MaterialTheme.typography.labelSmall,
                        // Drying is a normal dock job, so water blue like the Dock sheet, not an alert color.
                        color = AppColors.Water,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "12.3 m² so far · 25 min", in minutes as the official History shows time. */
internal fun cleanDetailLine(areaMm2: Long?, cleanTimeSec: Long?, cleaningNow: Boolean): String {
    // clean_area is reported in mm²; show m² with one decimal.
    val area = areaMm2?.let { mm2 ->
        val tenths = mm2 / 100_000
        "${tenths / 10}.${tenths % 10} m²"
    }
    val time = cleanTimeSec?.takeIf { it > 0 }?.let { DeviceStrings.fill(DeviceStrings.MINUTES, it / 60) }
    return buildList {
        if (area != null) add((if (cleaningNow) DeviceStrings.AREA_SO_FAR else DeviceStrings.AREA_LAST_CLEAN).replace("{area}", area))
        if (time != null) add(time)
    }.joinToString(" · ").ifEmpty { DeviceStrings.NO_CLEAN_YET }
}
