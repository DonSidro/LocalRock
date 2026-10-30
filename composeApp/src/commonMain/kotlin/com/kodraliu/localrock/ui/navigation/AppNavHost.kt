package com.kodraliu.localrock.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kodraliu.localrock.ui.onboarding.ServerSetupScreen
import com.kodraliu.localrock.ui.settings.APP_VERSION
import com.kodraliu.localrock.ui.settings.SettingsScreen
import com.kodraliu.localrock.ui.vacuum.CameraLiveViewScreen
import com.kodraliu.localrock.ui.vacuum.ScheduleScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDetailScreen
import com.kodraliu.localrock.ui.vacuum.VacuumHistoryScreen
import com.kodraliu.localrock.ui.vacuum.VacuumPinGoScreen
import com.kodraliu.localrock.ui.vacuum.VacuumRemoteScreen
import com.kodraliu.localrock.ui.vacuum.VacuumSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel
import com.kodraliu.localrock.ui.vacuum.MapEditScreen
import com.kodraliu.localrock.ui.vacuum.ZoneCleanScreen
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
    val lastOpenedDuid by container.appSettings.lastOpenedDuid.collectAsState()
    var splashDone by remember { mutableStateOf(false) }

    // Whether the launch has already gone into a robot (notification tap, auto-open, or a manual
    // pick). Survives rotation so this happens once per launch, not once per recomposition.
    var launchedIntoRobot by rememberSaveable { mutableStateOf(false) }


    LaunchedEffect(splashDone, introSeen, userData, serverUrl, demoMode) {
        if (!splashDone) return@LaunchedEffect
        val destination: Any = when {
            !introSeen -> Welcome
            demoMode -> DeviceList
            serverUrl.isNullOrBlank() -> ServerSetup
            userData == null -> Login
            else -> DeviceList
        }
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // A tapped notification names a robot. Go there once the app is past its first screens and
    // the list that robot lives in is loaded, replacing whatever was open above the list.
    val pendingOpen by container.pendingOpenDuid.collectAsState()
    val devices by container.deviceRepository.devices.collectAsState()
    LaunchedEffect(splashDone, userData, pendingOpen, devices) {
        val duid = pendingOpen ?: return@LaunchedEffect
        if (!splashDone || userData == null) return@LaunchedEffect
        if (devices.none { it.duid == duid && it.localKey != null }) return@LaunchedEffect
        container.consumePendingOpen()
        container.appSettings.setLastOpenedDuid(duid)
        launchedIntoRobot = true
        navController.navigate(VacuumGraph(duid)) { popUpTo<DeviceList>() }
    }

    // On launch, go straight into a robot once: the only paired robot, or the last one opened.
    // A notification tap wins — while one is pending this waits and the effect above handles it.
    LaunchedEffect(splashDone, userData, pendingOpen, devices, lastOpenedDuid) {
        if (launchedIntoRobot || !splashDone || userData == null || pendingOpen != null) return@LaunchedEffect
        val pairedDuids = devices.filter { it.localKey != null }.map { it.duid }
        val target = launchRobotTarget(pairedDuids, lastOpenedDuid) ?: return@LaunchedEffect
        launchedIntoRobot = true
        container.appSettings.setLastOpenedDuid(target)
        navController.navigate(VacuumGraph(target)) { popUpTo<DeviceList>() }
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

        composable<DeviceList> {
            val viewModel: DeviceListViewModel = viewModel { DeviceListViewModel(container) }
            DeviceListScreen(
                viewModel = viewModel,
                onSettings = { navController.navigate(AppSettings) },
                onDeviceClick = { device ->
                    container.appSettings.setLastOpenedDuid(device.duid)
                    launchedIntoRobot = true
                    navController.navigate(VacuumGraph(device.duid))
                },
                onAddVacuum = { navController.navigate(AddVacuum) },
            )
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
                    onMapEdit = { navController.navigate(VacuumMapEdit) },
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

            composable<VacuumMapEdit> { entry ->
                val graphEntry = remember(entry) { navController.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                MapEditScreen(
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

/**
 * The robot to open on launch, or null to stay on the device list: the only paired robot, or the
 * last one opened when there are several. Never guesses among several without a last-opened value.
 */
internal fun launchRobotTarget(pairedDuids: List<String>, lastOpenedDuid: String?): String? = when {
    pairedDuids.size == 1 -> pairedDuids.first()
    lastOpenedDuid != null && lastOpenedDuid in pairedDuids -> lastOpenedDuid
    else -> null
}
