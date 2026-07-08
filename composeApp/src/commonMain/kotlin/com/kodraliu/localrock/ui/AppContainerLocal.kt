package com.kodraliu.localrock.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kodraliu.localrock.shared.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided — wrap your content with App(container = ...)")
}
