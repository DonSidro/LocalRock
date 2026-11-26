package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.ui.AppColors

@Composable
internal fun batteryColor(battery: Int?): Color = when {
    battery == null -> MaterialTheme.colorScheme.onSurfaceVariant
    battery <= 15 -> MaterialTheme.colorScheme.error
    battery <= 40 -> AppColors.Warn
    else -> AppColors.Good
}

internal fun isBusyState(state: Int?): Boolean = when (state) {
    null,
    VacuumStateCodes.IDLE,
    VacuumStateCodes.NOT_CHARGING,
    VacuumStateCodes.CHARGING,
    VacuumStateCodes.CHARGER_DISCONNECTED,
    VacuumStateCodes.CHARGING_COMPLETE,
    VacuumStateCodes.OFFLINE,
    VacuumStateCodes.SHUTTING_DOWN -> false
    else -> true
}

@Composable
internal fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}

@Composable
internal fun stateTint(state: Int?): Color = when (state) {
    VacuumStateCodes.WASHING_MOP, VacuumStateCodes.GOING_TO_WASH_MOP,
    VacuumStateCodes.DRYING_MOP, VacuumStateCodes.WASHING_MOP_ALT -> MaterialTheme.colorScheme.tertiary
    VacuumStateCodes.ERROR, VacuumStateCodes.CHARGING_ERROR -> MaterialTheme.colorScheme.error
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
    VacuumStateCodes.STARTING, VacuumStateCodes.MANUAL_MODE,
    VacuumStateCodes.REMOTE_CONTROL_ACTIVE, VacuumStateCodes.PAUSED,
    VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.DOCKING,
    VacuumStateCodes.GOING_TO_TARGET, VacuumStateCodes.MAPPING -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
