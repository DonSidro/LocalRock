package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.rememberUse24HourClock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hour and minute steppers in the device's clock style. [hour] is always 0 to 23; on a 12-hour
 * device it is shown as 1 to 12 with an AM/PM button, and the button keeps the half of the day.
 */
@Composable
internal fun ClockStepper(hour: Int, minute: Int, onHourChange: (Int) -> Unit, onMinuteChange: (Int) -> Unit) {
    val use24 = rememberUse24HourClock()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (use24) {
            NumberStepper("Hour", hour, 0, 23) { onHourChange(it) }
        } else {
            NumberStepper("Hour", hour12Of(hour), 1, 12) { onHourChange(hour24Of(it, pm = hour >= 12)) }
        }
        Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        // One minute a step, with hold-to-repeat on the buttons, so any minute can be reached.
        NumberStepper("Min", minute, 0, 59) { onMinuteChange(it) }
        if (!use24) {
            OutlinedButton(
                onClick = { onHourChange((hour + 12) % 24) },
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Text(if (hour < 12) "AM" else "PM")
            }
        }
    }
}

/**
 * A round-cornered step button: one step when pressed, then more, quickly, for as long as it is held.
 * A plain button only reports a tap, which would make a minute 40 taps away.
 */
@Composable
internal fun StepButton(label: String, onStep: () -> Unit) {
    val latestStep by rememberUpdatedState(onStep)
    Surface(
        modifier = Modifier.size(36.dp).pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    latestStep()
                    coroutineScope {
                        val repeating = launch {
                            delay(HOLD_DELAY_MS)
                            while (true) {
                                latestStep()
                                delay(HOLD_REPEAT_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeating.cancel()
                    }
                },
            )
        },
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label) }
    }
}

private const val HOLD_DELAY_MS = 400L
private const val HOLD_REPEAT_MS = 70L

/** 12-hour clock face for [hour], 0 to 23: 0 is 12, 13 is 1. */
internal fun hour12Of(hour: Int): Int = if (hour % 12 == 0) 12 else hour % 12

/** 0 to 23 for a 12-hour clock face of [hour12], 1 to 12, in the afternoon when [pm]. */
internal fun hour24Of(hour12: Int, pm: Boolean): Int = (hour12 % 12) + if (pm) 12 else 0

/** A time of day the way the device writes it: "17:05", or "05:05 PM" on a 12-hour clock. */
internal fun formatClock(hour: Int, minute: Int, use24: Boolean): String {
    val mm = minute.toString().padStart(2, '0')
    if (use24) return "${hour.toString().padStart(2, '0')}:$mm"
    return "${hour12Of(hour).toString().padStart(2, '0')}:$mm ${if (hour < 12) "AM" else "PM"}"
}
