package com.kodraliu.localrock.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun platformDynamicColorScheme(useDark: Boolean): ColorScheme? = null

/**
 * Not implemented on iOS yet: the status bar follows the phone's appearance, so its text can be
 * hard to read when the app's Light/Dark setting differs from it. Fixing it needs the hosting
 * UIViewController's interface style set from Kotlin/Native, which has to be built on a Mac.
 */
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit
