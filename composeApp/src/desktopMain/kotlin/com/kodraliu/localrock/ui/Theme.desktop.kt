package com.kodraliu.localrock.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** No wallpaper-based colours on desktop; the app's own scheme is used. */
@Composable
actual fun platformDynamicColorScheme(useDark: Boolean): ColorScheme? = null

/** The window frame belongs to the OS; there are no system bars to tint. */
@Composable
actual fun SystemBarsAppearance(darkTheme: Boolean) = Unit
