package com.kodraliu.localrock

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kodraliu.localrock.shared.AppContainer
import kotlinx.coroutines.launch
import com.kodraliu.localrock.shared.settings.ThemeMode
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.VacDarkColorScheme
import com.kodraliu.localrock.ui.VacLightColorScheme
import com.kodraliu.localrock.ui.messages.MessageBannerArea
import com.kodraliu.localrock.ui.messages.TransientMessageHost
import com.kodraliu.localrock.ui.navigation.AppNavHost
import com.kodraliu.localrock.ui.platformDynamicColorScheme

@Composable
fun App(container: AppContainer) {
    val themeMode by container.appSettings.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = platformDynamicColorScheme(useDark)
        ?: if (useDark) VacDarkColorScheme else VacLightColorScheme


    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, container) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch { container.onEnterForeground() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalAppContainer provides container) {
        MaterialTheme(colorScheme = colorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    MessageBannerArea(container.messages)
                    Box(Modifier.weight(1f)) {
                        AppNavHost()
                        TransientMessageHost(container.messages)
                    }
                }
            }
        }
    }
}
