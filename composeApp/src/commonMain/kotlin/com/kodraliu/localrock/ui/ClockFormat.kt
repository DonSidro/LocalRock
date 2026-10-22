package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable

/** True when the device is set to a 24-hour clock, false for 12-hour with AM and PM. */
@Composable
expect fun rememberUse24HourClock(): Boolean
