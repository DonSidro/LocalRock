package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.Supply
import com.kodraliu.localrock.shared.vacuum.SupplyLevel
import com.kodraliu.localrock.shared.vacuum.supplyLevel
import com.kodraliu.localrock.shared.vacuum.supplyLife
import com.kodraliu.localrock.shared.vacuum.visibleSupplies
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.strings.SecondaryStrings
import kotlinx.coroutines.launch

internal fun Supply.label(): String = when (this) {
    Supply.MAIN_BRUSH -> SecondaryStrings.MAIN_BRUSH
    Supply.SIDE_BRUSH -> SecondaryStrings.SIDE_BRUSH
    Supply.FILTER -> SecondaryStrings.FILTER
    Supply.SENSORS -> SecondaryStrings.SENSORS
    Supply.WATER_TANK_FILTER -> SecondaryStrings.WATER_TANK_FILTER
    Supply.DIRTY_WATER_TANK -> SecondaryStrings.DIRTY_WATER_TANK
    Supply.CLEANING_TRAY -> SecondaryStrings.CLEANING_TRAY
    Supply.MAINTENANCE_BRUSH -> SecondaryStrings.MAINTENANCE_BRUSH
    Supply.DUSTBIN -> SecondaryStrings.DUSTBIN
    Supply.MOP -> SecondaryStrings.MOP
    Supply.DUST_BAG -> SecondaryStrings.DUST_BAG
    Supply.ROLLER_MOP_COVER -> SecondaryStrings.ROLLER_MOP_COVER
    Supply.DIRTY_WATER_CHANNEL -> SecondaryStrings.DIRTY_WATER_CHANNEL
}

/** The official reset confirmation for each row. */
internal fun Supply.resetConfirm(): String = when (this) {
    Supply.MAIN_BRUSH -> SecondaryStrings.CONFIRM_MAIN_BRUSH
    Supply.SIDE_BRUSH -> SecondaryStrings.CONFIRM_SIDE_BRUSH
    Supply.FILTER -> SecondaryStrings.CONFIRM_FILTER
    Supply.SENSORS -> SecondaryStrings.CONFIRM_SENSORS
    Supply.WATER_TANK_FILTER -> SecondaryStrings.CONFIRM_WATER_TANK_FILTER
    Supply.DIRTY_WATER_TANK -> SecondaryStrings.CONFIRM_DIRTY_WATER_TANK
    Supply.CLEANING_TRAY -> SecondaryStrings.CONFIRM_CLEANING_TRAY
    Supply.MAINTENANCE_BRUSH -> SecondaryStrings.CONFIRM_MAINTENANCE_BRUSH
    Supply.DUSTBIN -> SecondaryStrings.CONFIRM_DUSTBIN
    Supply.MOP, Supply.DUST_BAG, Supply.ROLLER_MOP_COVER, Supply.DIRTY_WATER_CHANNEL -> ""
}

private fun Supply.description(): String? = when (this) {
    Supply.MAIN_BRUSH -> SecondaryStrings.DESC_MAIN_BRUSH
    Supply.SIDE_BRUSH -> SecondaryStrings.DESC_SIDE_BRUSH
    Supply.FILTER -> SecondaryStrings.DESC_FILTER
    Supply.SENSORS -> SecondaryStrings.DESC_SENSORS
    Supply.WATER_TANK_FILTER -> SecondaryStrings.DESC_WATER_TANK_FILTER
    Supply.CLEANING_TRAY -> SecondaryStrings.DESC_CLEANING_TRAY
    Supply.ROLLER_MOP_COVER -> SecondaryStrings.DESC_ROLLER_MOP_COVER
    Supply.DIRTY_WATER_CHANNEL -> SecondaryStrings.DESC_DIRTY_WATER_CHANNEL
    else -> null
}

/** Rows without a counter: parts you rinse say Clean as needed, the rest Replace as needed. */
private fun Supply.asNeeded(): String =
    if (this == Supply.ROLLER_MOP_COVER || this == Supply.DIRTY_WATER_CHANNEL) SecondaryStrings.CLEAN_AS_NEEDED else SecondaryStrings.REPLACE_AS_NEEDED

/** "Estimated N hours remaining" for timed parts, "N time(s)" for run counters. */
private fun remainingText(supply: Supply, remaining: Long): String =
    if (supply.counted == Supply.Counted.SECONDS) SecondaryStrings.HOURS_REMAINING.replace("{hour}", "$remaining")
    else SecondaryStrings.TIMES_LEFT.replace("{n}", "$remaining")

@Composable
private fun levelColor(level: SupplyLevel): Color = when (level) {
    SupplyLevel.OK -> MaterialTheme.colorScheme.primary
    SupplyLevel.LOW -> AppColors.Warn
    SupplyLevel.OUT -> MaterialTheme.colorScheme.error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaintenanceScreen(
    consumables: ConsumableStatus?,
    rows: List<Supply>,
    onRefresh: () -> Unit,
    onOpen: (Supply) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(SecondaryStrings.MAINTENANCE) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") } },
            )
        },
    ) { padding ->
        CenteredContent {
            if (consumables == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.Build, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(SecondaryStrings.CONSUMABLE_LOAD_FAILED, style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = onRefresh) { Text(SecondaryStrings.RETRY) }
                    }
                }
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows, key = { it.name }) { supply -> SupplyRow(supply, consumables) { onOpen(supply) } }
                }
            }
        }
    }
}

@Composable
private fun SupplyRow(supply: Supply, status: ConsumableStatus, onClick: () -> Unit) {
    val used = supply.used(status)
    val life = used?.let { supplyLife(supply, it) }
    Surface(
        onClick = onClick,
        enabled = life != null,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (life != null) LifeRing(life.percent, 48.dp) else SupplyIcon(supply, 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(supply.label(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    if (life != null) remainingText(supply, life.remaining) else supply.asNeeded(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (life != null && supplyLevel(life.percent) == SupplyLevel.OUT) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (life != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Rows without a counter get an icon in a plain track ring the same size as LifeRing.
@Composable
private fun SupplyIcon(supply: Supply, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { 0f },
            modifier = Modifier.size(size),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = 4.dp,
        )
        Icon(
            if (supply == Supply.DUST_BAG) Icons.Default.Inventory2 else Icons.Default.CleaningServices,
            null,
            Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Percent ring with the number inside, coloured by the official thresholds. */
@Composable
private fun LifeRing(percent: Int, size: Dp) {
    val color = levelColor(supplyLevel(percent))
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.size(size),
            color = color,
            trackColor = if (percent <= 0) color else MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeWidth = if (size > 80.dp) 8.dp else 4.dp,
        )
        Text(
            "$percent%",
            style = if (size > 80.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The supply detail page: ring, Used / Remaining, description and Reset with the official confirm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaintenanceDetail(
    supply: Supply,
    consumables: ConsumableStatus?,
    onReset: suspend (Supply) -> Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    val used = consumables?.let { supply.used(it) }
    val life = used?.let { supplyLife(supply, it) }
    val unit = if (supply.counted == Supply.Counted.SECONDS) SecondaryStrings.HOURS_UNIT else SecondaryStrings.TIMES_UNIT
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(supply.label()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (life == null) {
                    Text(supply.asNeeded(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LifeRing(life.percent, 160.dp)
                    Text(remainingText(supply, life.remaining), style = MaterialTheme.typography.bodyMedium)
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            UsageCell("${life.used} $unit", SecondaryStrings.USED, Modifier.weight(1f))
                            UsageCell("${life.remaining} $unit", SecondaryStrings.REMAINING, Modifier.weight(1f))
                        }
                    }
                }
                supply.description()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (supply.field != null && life != null) {
                    Button(onClick = { confirm = true }, enabled = !resetting, modifier = Modifier.fillMaxWidth()) {
                        Text(SecondaryStrings.RESET)
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(supply.resetConfirm()) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    resetting = true
                    scope.launch {
                        if (!onReset(supply)) snackbar.showSnackbar(SecondaryStrings.RESET_FAILED)
                        resetting = false
                    }
                }) { Text(if (supply == Supply.SENSORS) SecondaryStrings.CONFIRM_SENSORS_BUTTON else SecondaryStrings.OK) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(SecondaryStrings.CANCEL) } },
        )
    }
}

@Composable
private fun UsageCell(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun VacuumViewModel.resetSupply(supply: Supply): Boolean {
    val field = supply.field ?: return false
    return runCatching {
        repository.resetConsumable(field)
        repository.fetchConsumableStatus()
    }.isSuccess
}

/** [onOpen] gets the [Supply] name for the VacuumMaintenanceDetail route. */
@Composable
fun VacuumMaintenanceScreen(viewModel: VacuumViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repository = viewModel.repository
    val scope = rememberCoroutineScope()
    val consumables by repository.consumableStatus.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    LaunchedOnEntry { runCatching { repository.fetchConsumableStatus() } }
    MaintenanceScreen(
        consumables = consumables,
        rows = consumables?.let { visibleSupplies(it, capabilities.advertised.rollerMop, capabilities.hasAutoEmptySettings) }.orEmpty(),
        onRefresh = { scope.launch { runCatching { repository.fetchConsumableStatus() } } },
        onOpen = { onOpen(it.name) },
        onBack = onBack,
    )
}

/** Body for the VacuumMaintenanceDetail(consumable) route; [consumable] is a [Supply] name. */
@Composable
fun VacuumMaintenanceDetailScreen(viewModel: VacuumViewModel, consumable: String, onBack: () -> Unit) {
    val consumables by viewModel.repository.consumableStatus.collectAsState()
    val supply = Supply.entries.firstOrNull { it.name == consumable }
    if (supply == null) {
        LaunchedEffect(consumable) { onBack() }
        return
    }
    LaunchedOnEntry { runCatching { viewModel.repository.fetchConsumableStatus() } }
    MaintenanceDetail(supply, consumables, onReset = { viewModel.resetSupply(it) }, onBack = onBack)
}
