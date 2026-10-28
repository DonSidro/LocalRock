package com.kodraliu.localrock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.home.HomeShell
import com.kodraliu.localrock.ui.intro.SplashScreen
import com.kodraliu.localrock.ui.intro.WelcomeScreen
import com.kodraliu.localrock.ui.login.LoginScreen
import com.kodraliu.localrock.ui.onboarding.AddVacuumScreen
import com.kodraliu.localrock.ui.onboarding.ServerSetupScreen
import com.kodraliu.localrock.ui.settings.APP_VERSION
import com.kodraliu.localrock.ui.settings.SettingsScreen
import com.kodraliu.localrock.ui.whatsnew.WhatsNewSheet
import com.kodraliu.localrock.ui.whatsnew.shouldShowWhatsNew
import com.kodraliu.localrock.ui.whatsnew.whatsNewFor

@Composable
fun AppNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val userData by container.authRepository.userData.collectAsState()
    val serverUrl by container.appSettings.serverBaseUrl.collectAsState()
    val introSeen by container.appSettings.introSeen.collectAsState()
    val demoMode by container.appSettings.demoMode.collectAsState()
    val lastSeenVersion by container.appSettings.lastSeenVersion.collectAsState()
    var splashDone by remember { mutableStateOf(false) }


    LaunchedEffect(splashDone, introSeen, userData, serverUrl, demoMode) {
        if (!splashDone) return@LaunchedEffect
        val destination: Any = when {
            !introSeen -> Welcome
            demoMode -> Home
            serverUrl.isNullOrBlank() -> ServerSetup
            userData == null -> Login
            else -> Home
        }
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = Splash) {

        composable<Splash> {
            SplashScreen(onTimeout = { splashDone = true })
        }

        composable<Welcome> {
            WelcomeScreen()
        }

        composable<ServerSetup> { entry ->
            // Reached either as the first-run step (nothing to go back to) or from Login.
            val canGoBack = remember(entry) { navController.previousBackStackEntry != null }
            ServerSetupScreen(
                onDone = {
                    navController.navigate(Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onBack = if (canGoBack) ({ navController.popBackStack(); Unit }) else null,
            )
        }

        composable<Login> {
            LoginScreen(onServerSetup = { navController.navigate(ServerSetup) })
        }

        composable<Home> {
            HomeShell(topNav = navController)
            // Once after an update, over the first screen that is really the app.
            val release = whatsNewFor(APP_VERSION)
            if (release != null && shouldShowWhatsNew(introSeen, lastSeenVersion, APP_VERSION)) {
                WhatsNewSheet(release = release, onDismiss = { container.appSettings.setLastSeenVersion(APP_VERSION) })
            }
        }

        composable<AppSettings> {
            SettingsScreen(
                onDone = { navController.popBackStack() },
                allowCancel = !serverUrl.isNullOrBlank(),
            )
        }

        composable<AddVacuum> {
            AddVacuumScreen(onBack = { navController.popBackStack() })
        }
    }
}
