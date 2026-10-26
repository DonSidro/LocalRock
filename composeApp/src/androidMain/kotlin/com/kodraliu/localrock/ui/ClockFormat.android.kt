package com.kodraliu.localrock.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberUse24HourClock(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}
