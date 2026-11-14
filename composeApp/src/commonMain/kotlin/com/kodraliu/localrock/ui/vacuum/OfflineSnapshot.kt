package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.model.LastKnown
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * "8:34 PM" for a time today, "Oct 21, 8:34 PM" for an earlier day, in [zone]; null when [iso]
 * is missing or not a timestamp.
 */
internal fun formatLastKnownTime(
    iso: String?,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val at = iso?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone)
    val hour12 = if (local.hour % 12 == 0) 12 else local.hour % 12
    val clock = "$hour12:${local.minute.toString().padStart(2, '0')} ${if (local.hour < 12) "AM" else "PM"}"
    return if (sameDay(local, today)) clock else "${MONTHS[local.monthNumber - 1]} ${local.dayOfMonth}, $clock"
}

private fun sameDay(a: LocalDateTime, b: LocalDateTime) = a.year == b.year && a.dayOfYear == b.dayOfYear

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * The lines to show for a robot that has stopped answering: when it went quiet, and what it last
 * said (battery, state, any error). An error code wins over the plain state.
 */
internal fun lastKnownLines(lastKnown: LastKnown, now: Instant, zone: TimeZone = TimeZone.currentSystemDefault()): List<String> {
    val lines = mutableListOf<String>()
    val since = formatLastKnownTime(lastKnown.offlineAt, now, zone)
    val seen = formatLastKnownTime(lastKnown.lastSeenAt, now, zone)
    when {
        since != null -> lines += "Offline since $since"
        seen != null -> lines += "Last heard from $seen"
        else -> lines += "Not answering"
    }
    val status = lastKnown.status
    val details = buildList {
        status?.battery?.let { add("Battery $it%") }
        val error = status?.errorCode ?: 0
        if (error != 0) add(VacuumErrorCodes.describe(error))
        else status?.state?.let { add(VacuumStateCodes.describe(it)) }
    }
    if (details.isNotEmpty()) lines += "Last reported: " + details.joinToString(", ")
    if (lastKnown.position?.robot != null) lines += "Showing where it was last seen"
    return lines
}

/** Shown over the map while the robot does not answer. */
@Composable
internal fun OfflineSnapshotBadge(lines: List<String>, modifier: Modifier = Modifier) {
    if (lines.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
