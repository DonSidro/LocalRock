package com.kodraliu.localrock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

// Not compiled on the Windows machine this was written on; verify with an Xcode build.
@Composable
actual fun rememberUse24HourClock(): Boolean = remember {
    // "j" is the locale's preferred hour; the pattern has an "a" only when that is 12-hour.
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale) ?: ""
    !pattern.contains('a')
}
