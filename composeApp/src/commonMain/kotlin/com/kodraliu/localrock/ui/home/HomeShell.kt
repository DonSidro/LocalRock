package com.kodraliu.localrock.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.onboarding.vacuumPairingSupported
import com.kodraliu.localrock.ui.LocalAppContainer
import com.kodraliu.localrock.ui.adaptive.LocalWindowLayout
import com.kodraliu.localrock.ui.devices.DeviceListViewModel
import com.kodraliu.localrock.ui.icons.RobotVacuum
import com.kodraliu.localrock.ui.navigation.AddVacuum
import com.kodraliu.localrock.ui.navigation.AppSettings
import com.kodraliu.localrock.ui.navigation.RobotPlaceholder
import com.kodraliu.localrock.ui.navigation.VacuumCamera
import com.kodraliu.localrock.ui.navigation.VacuumGraph
import com.kodraliu.localrock.ui.navigation.VacuumHistory
import com.kodraliu.localrock.ui.navigation.VacuumMain
import com.kodraliu.localrock.ui.navigation.VacuumMapEdit
import com.kodraliu.localrock.ui.navigation.VacuumPinGo
import com.kodraliu.localrock.ui.navigation.VacuumRemote
import com.kodraliu.localrock.ui.navigation.VacuumSchedule
import com.kodraliu.localrock.ui.navigation.VacuumSettings
import com.kodraliu.localrock.ui.navigation.VacuumZoneClean
import com.kodraliu.localrock.ui.vacuum.CameraLiveViewScreen
import com.kodraliu.localrock.ui.vacuum.MapEditScreen
import com.kodraliu.localrock.ui.vacuum.ScheduleScreen
import com.kodraliu.localrock.ui.vacuum.VacuumDetailScreen
import com.kodraliu.localrock.ui.vacuum.VacuumHistoryScreen
import com.kodraliu.localrock.ui.vacuum.VacuumPinGoScreen
import com.kodraliu.localrock.ui.vacuum.VacuumRemoteScreen
import com.kodraliu.localrock.ui.vacuum.VacuumSettingsScreen
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel
import com.kodraliu.localrock.ui.vacuum.ZoneCleanScreen
import kotlinx.coroutines.launch

/**
 * The home surface after login: a robot sidebar (persistent on wide windows, a drawer on phones)
 * beside the selected robot's nested navigation. Selecting a robot in the sidebar swaps the nested
 * back stack to that robot's graph, which detaches the previous robot's session and attaches the
 * new one — one live robot at a time, exactly as the pre-sidebar list did.
 */
@Composable
fun HomeShell(topNav: NavHostController) {
    val container = LocalAppContainer.current
    val listViewModel: DeviceListViewModel = viewModel { DeviceListViewModel(container) }
    val devices by listViewModel.devices.collectAsState()
    val products by listViewModel.productsByDuid.collectAsState()
    val reachability by listViewModel.reachability.collectAsState()
    val refreshing by listViewModel.refreshing.collectAsState()
    val error by listViewModel.error.collectAsState()

    val layout = LocalWindowLayout.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val nestedNav = rememberNavController()

    // The selected robot defaults to the first paired device; if it disappears, re-default so the
    // pane never points at a robot that is no longer there.
    var selectedDuid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(devices) {
        if (selectedDuid == null || devices.none { it.duid == selectedDuid && it.localKey != null }) {
            selectedDuid = devices.firstOrNull { it.localKey != null }?.duid
        }
    }

    // A tapped notification names a robot: select it once it is loaded and paired.
    val pendingOpen by container.pendingOpenDuid.collectAsState()
    LaunchedEffect(devices, pendingOpen) {
        val duid = pendingOpen ?: return@LaunchedEffect
        if (devices.none { it.duid == duid && it.localKey != null }) return@LaunchedEffect
        container.consumePendingOpen()
        selectedDuid = duid
    }

    // Swap the nested back stack whenever the selected robot changes.
    LaunchedEffect(selectedDuid) {
        val duid = selectedDuid ?: return@LaunchedEffect
        nestedNav.navigate(VacuumGraph(duid)) {
            popUpTo(nestedNav.graph.id) { inclusive = true }
        }
    }

    if (devices.isEmpty()) {
        HomeEmptyState(
            refreshing = refreshing,
            error = error,
            canPair = vacuumPairingSupported,
            onAddVacuum = { topNav.navigate(AddVacuum) },
            onRetry = listViewModel::refresh,
        )
        return
    }

    val sidebar: @Composable () -> Unit = {
        RobotSidebar(
            devices = devices,
            productsByDuid = products,
            reachability = reachability,
            selectedDuid = selectedDuid,
            onSelectRobot = { device ->
                selectedDuid = device.duid
                if (!layout.twoPane) scope.launch { drawerState.close() }
            },
            onAddVacuum = if (vacuumPairingSupported) ({ topNav.navigate(AddVacuum) }) else null,
            onSettings = { topNav.navigate(AppSettings) },
            onSignOut = { listViewModel.logout() },
        )
    }

    if (layout.twoPane) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(288.dp).fillMaxHeight().statusBarsPadding()) {
                sidebar()
            }
            VerticalDivider()
            RobotContent(
                nestedNav = nestedNav,
                container = container,
                onOpenSidebar = null,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { ModalDrawerSheet { sidebar() } },
        ) {
            RobotContent(
                nestedNav = nestedNav,
                container = container,
                onOpenSidebar = { scope.launch { drawerState.open() } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The selected robot's nested navigation. Always composed (even before a robot is selected) so the
 * nav controller's graph is set before [HomeShell]'s selection effect navigates.
 */
@Composable
private fun RobotContent(
    nestedNav: NavHostController,
    container: AppContainer,
    onOpenSidebar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = nestedNav,
        startDestination = RobotPlaceholder,
        modifier = modifier,
    ) {
        composable<RobotPlaceholder> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No robot ready to control",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        navigation<VacuumGraph>(startDestination = VacuumMain) {
            composable<VacuumMain> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumDetailScreen(
                    viewModel = viewModel,
                    onOpenSidebar = onOpenSidebar,
                    onSettings = { nestedNav.navigate(VacuumSettings) },
                    onZoneClean = { nestedNav.navigate(VacuumZoneClean) },
                    onMapEdit = { nestedNav.navigate(VacuumMapEdit) },
                    onSchedule = { nestedNav.navigate(VacuumSchedule) },
                    onCamera = { nestedNav.navigate(VacuumCamera) },
                    onRemote = { nestedNav.navigate(VacuumRemote) },
                    onPinGo = { nestedNav.navigate(VacuumPinGo) },
                    onHistory = { nestedNav.navigate(VacuumHistory) },
                )
            }

            composable<VacuumRemote> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumRemoteScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumPinGo> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumPinGoScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumHistory> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumHistoryScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumCamera> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                CameraLiveViewScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumSettings> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                VacuumSettingsScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumZoneClean> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                ZoneCleanScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumMapEdit> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                MapEditScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }

            composable<VacuumSchedule> { entry ->
                val graphEntry = remember(entry) { nestedNav.getBackStackEntry<VacuumGraph>() }
                val duid = graphEntry.toRoute<VacuumGraph>().duid
                val viewModel: VacuumViewModel = viewModel(graphEntry) { VacuumViewModel(duid, container) }
                ScheduleScreen(viewModel = viewModel, onBack = { nestedNav.popBackStack() })
            }
        }
    }
}

/** Full-screen empty state when there are no robots (also carries the refresh-error case). */
@Composable
private fun HomeEmptyState(
    refreshing: Boolean,
    error: String?,
    canPair: Boolean,
    onAddVacuum: () -> Unit,
    onRetry: () -> Unit,
) {
    if (refreshing && error == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (error != null) Icons.Default.ErrorOutline else RobotVacuum,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (error != null) "Can't reach your server" else "No robots yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                error ?: "Add your first vacuum, or pull down to check the server again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
            if (error != null) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try again")
                }
            } else if (canPair) {
                Button(onClick = onAddVacuum) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add vacuum")
                }
            }
        }
    }
}
