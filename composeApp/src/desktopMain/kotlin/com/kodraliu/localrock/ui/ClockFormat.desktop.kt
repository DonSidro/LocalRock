package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.text.SimpleDateFormat

/** Follows the locale's short time format, which is how the JVM sees the OS clock preference. */
@Composable
actual fun rememberUse24HourClock(): Boolean = remember {
    val pattern = (DateFormat.getTimeInstance(DateFormat.SHORT) as? SimpleDateFormat)?.toPattern()
    pattern == null || 'a' !in pattern
}
