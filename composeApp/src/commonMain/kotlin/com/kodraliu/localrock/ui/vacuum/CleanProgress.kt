package com.kodraliu.localrock.ui.vacuum

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanProgress
import com.kodraliu.localrock.ui.AppColors

/**
 * The thin bar under the status headline: how far through its job the robot is.
 *
 * The robot answers a poll with a jump at a time, so the fraction animates instead of snapping,
 * and it never moves backwards within one run — a bar that walks backwards reads as a bug. The
 * monotonic part is remembered here, not in the model, because the model is a pure projection of
 * the robot's latest words and the robot genuinely does occasionally report a lower figure.
 */
@Composable
fun CleaningProgressBar(
    progress: CleanProgress,
    /** "18 min" or null; shown when the robot does not report a percent. */
    elapsedLabel: String?,
    modifier: Modifier = Modifier,
) {
    if (!progress.jobActive) return

    var maxSinceStart by remember { mutableStateOf(0) }
    LaunchedEffect(progress.jobActive, progress.percent) {
        if (!progress.jobActive) maxSinceStart = 0
        else progress.percent?.let { if (it > maxSinceStart) maxSinceStart = it }
    }
    val fraction by animateFloatAsState(
        targetValue = maxSinceStart / 100f,
        label = "cleanProgress",
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = if (progress.phase == CleanProgress.Phase.Break) AppColors.Warn else AppColors.Good,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        val caption = when {
            progress.percent != null -> "${progress.percent}%" + breakSuffix(progress)
            elapsedLabel != null -> "In progress · $elapsedLabel"
            else -> "In progress"
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                phaseLabel(progress),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun breakSuffix(progress: CleanProgress): String = when (progress.phase) {
    CleanProgress.Phase.Break -> " · on a break"
    CleanProgress.Phase.Paused -> " · paused"
    else -> ""
}

@Composable
private fun phaseLabel(progress: CleanProgress): String = when (progress.phase) {
    CleanProgress.Phase.Cleaning -> "Cleaning"
    CleanProgress.Phase.Paused -> "Paused"
    CleanProgress.Phase.Break -> "On a break"
    CleanProgress.Phase.Over -> ""
}
