package com.kodraliu.localrock

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kodraliu.localrock.shared.AppContainer
import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

/** Desktop entry point: one window running the same Compose app as Android and iOS. */
fun main() {
    val container = AppContainer(PreferencesSettings(Preferences.userRoot().node("com/kodraliu/localrock/vaclocal")))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "LocalRock",
            state = rememberWindowState(size = DpSize(1200.dp, 850.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(420, 600)
            App(container)
        }
    }
}
