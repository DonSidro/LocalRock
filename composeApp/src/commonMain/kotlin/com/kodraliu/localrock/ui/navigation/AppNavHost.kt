package com.kodraliu.localrock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.devices.DeviceListScreen
import com.kodraliu.localrock.ui.devices.DeviceListViewModel
import com.kodraliu.localrock.ui.intro.SplashScreen
import com.kodraliu.localrock.ui.intro.WelcomeScreen
import com.kodraliu.localrock.ui.login.LoginScreen
import com.kodraliu.localrock.ui.onboarding.AddVacuumScreen
import com.kodraliu.localrock.ui.settings.SettingsScreen
import com.kodraliu.localrock.ui.vacuum.CameraLiveViewScreen
import com.kodraliu.localrock.ui.vacuum.ScheduleScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDetailScreen
import com.kodraliu.localrock.ui.vacuum.VacuumHistoryScreen
import com.kodraliu.localrock.ui.vacuum.VacuumPinGoScreen
import com.kodraliu.localrock.ui.vacuum.VacuumRemoteScreen
import com.kodraliu.localrock.ui.vacuum.VacuumSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel
import com.kodraliu.localrock.ui.vacuum.ZoneCleanScreen

@Composable
fun AppNavHost() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val userData by container.authRepository.userData.collectAsState()
    val serverUrl by container.appSettings.serverBaseUrl.collectAsState()
    val introSeen by container.appSettings.introSeen.collectAsState()
    val demoMode by container.appSettings.demoMode.collectAsState()
    var splashDone by remember { mutableStateOf(false) }


    LaunchedEffect(splashDone, introSeen, userData, serverUrl, demoMode) {
        if (!splashDone) return@LaunchedEffect
        val destination: Any = when {
            !introSeen -> Welcome
            demoMode -> DeviceList
            serverUrl.isNullOrBlank() -> AppSettings
            userData == null -> Login
            else -> DeviceList
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

        composable<Login> {
            LoginScreen(onSettings = { navController.navigate(AppSettings) })
        }

        composable<DeviceList> {
            val viewModel: DeviceListViewModel = viewModel { DeviceListViewModel(container) }
            DeviceListScreen(
                viewModel = viewModel,
                onSettings = { navController.navigate(AppSettings) },
                onDeviceClick = { device -> navController.navigate(VacuumGraph(device.duid)) },
                onAddVacuum = { navController.navigate(AddVacuum) },
            )
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

        navigation<VacuumGraph>(startDestination = VacuumMain) {

            composable<VacuumMain> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack<VacuumGraph>(inclusive = true) },
                    onSettings = { navController.navigate(VacuumSettings) },
                    onZoneClean = { navController.navigate(VacuumZoneClean) },
                    onSchedule = { navController.navigate(VacuumSchedule) },
                    onCamera = { navController.navigate(VacuumCamera) },
                    onRemote = { navController.navigate(VacuumRemote) },
                    onPinGo = { navController.navigate(VacuumPinGo) },
                    onHistory = { navController.navigate(VacuumHistory) },
                )
            }

            composable<VacuumRemote> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumRemoteScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumPinGo> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumPinGoScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumHistory> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumHistoryScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumCamera> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                CameraLiveViewScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumSettings> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumZoneClean> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                ZoneCleanScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable<VacuumSchedule> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                ScheduleScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
