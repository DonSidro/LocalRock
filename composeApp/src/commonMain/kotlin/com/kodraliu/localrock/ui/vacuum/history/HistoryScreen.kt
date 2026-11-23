package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanSummary
import com.kodraliu.localrock.shared.vacuum.CleanTimeline
import com.kodraliu.localrock.shared.vacuum.CleanTimelineLabels
import com.kodraliu.localrock.shared.vacuum.CleanTimelineStep
import com.kodraliu.localrock.shared.vacuum.PatrolThumbnail
import com.kodraliu.localrock.shared.vacuum.areaParts
import com.kodraliu.localrock.shared.vacuum.cleanAreaLabel
import com.kodraliu.localrock.shared.vacuum.cleanDurationLabel
import com.kodraliu.localrock.shared.vacuum.cleanRecordTags
import com.kodraliu.localrock.shared.vacuum.cleaningRecords
import com.kodraliu.localrock.shared.vacuum.cruiseRecords
import com.kodraliu.localrock.shared.vacuum.finishReasonLabel
import com.kodraliu.localrock.shared.vacuum.historyRowSubtitle
import com.kodraliu.localrock.shared.vacuum.historyStamp
import com.kodraliu.localrock.shared.vacuum.isCruise
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.timelineClock
import com.kodraliu.localrock.shared.vacuum.totalAreaLabel
import com.kodraliu.localrock.shared.vacuum.totalMinutes
import com.kodraliu.localrock.shared.vacuum.workingTimes
import com.kodraliu.localrock.shared.vacuum.workingTimesHelp
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.strings.SecondaryStrings
import kotlinx.coroutines.launch

@Composable
fun VacuumHistoryScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val repository = viewModel.repository
    val mapRotation by viewModel.mapRotation.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val scope = rememberCoroutineScope()
    val cleanHistory by repository.cleanHistory.collectAsState()
    val cleanSummary by repository.cleanSummary.collectAsState()
    val rooms by repository.rooms.collectAsState()
    LaunchedEffect(Unit) {
        runCatching { repository.fetchCleanHistory() }
        runCatching { repository.fetchCleanSummary() }
    }
    HistoryScreen(
        records = cleanHistory,
        summary = cleanSummary,
        cruiseTabSupported = capabilities.cruiseHistory,
        dirtMapSupported = capabilities.dirtMap,
        loadMap = { repository.fetchCleanRecordMap(it) },
        loadThumbnails = { task, x, y -> repository.fetchPatrolThumbnails(task, x, y) },
        mapRotation = mapRotation,
        loadTimeline = repository::fetchCleanTimeline,
        roomName = { id -> rooms.firstOrNull { it.id == id }?.name },
        knownRooms = rooms,
        onRefresh = {
            scope.launch {
                runCatching { repository.fetchCleanHistory() }
                runCatching { repository.fetchCleanSummary() }
            }
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    records: List<CleanRecord>,
    summary: CleanSummary?,
    cruiseTabSupported: Boolean,
    loadMap: suspend (beginEpoch: Long) -> ParsedMap,
    loadThumbnails: suspend (taskId: Long, x: Int, y: Int) -> List<PatrolThumbnail>,
    mapRotation: Int,
    loadTimeline: suspend (CleanRecord) -> List<CleanTimelineStep>?,
    roomName: (Int) -> String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    dirtMapSupported: Boolean = false,
    knownRooms: List<ParsedMapRoom> = emptyList(),
) {
    var selected by remember { mutableStateOf<CleanRecord?>(null) }
    var cruiseTab by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    val open = selected
    // The Cruise tab follows the official gate, and also shows when the robot already listed cruise runs.
    val hasCruise = cruiseTabSupported || (summary?.patrolCount ?: 0) > 0 || records.any { it.isCruise }
    val showCruise = hasCruise && cruiseTab
    val shown = when {
        !hasCruise -> records.sortedByDescending { it.beginEpoch }
        showCruise -> records.cruiseRecords()
        else -> records.cleaningRecords().sortedByDescending { it.beginEpoch }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            open == null -> SecondaryStrings.HISTORY
                            open.isCruise -> cruiseDetailTitle(open)
                            else -> historyStamp(open.beginEpoch)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (open == null) onBack() else selected = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (open == null) {
                        IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            if (open != null) {
                if (open.isCruise) {
                    CruiseRecordDetail(open, loadMap, loadThumbnails, mapRotation, Modifier.padding(padding))
                } else {
                    CleanRecordDetail(open, loadMap, loadTimeline, roomName, knownRooms, mapRotation, dirtMapSupported, Modifier.padding(padding))
                }
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (summary != null) {
                        item { HistoryHeader(summary, onHelp = { helpOpen = true }) }
                    }
                    if (hasCruise) {
                        item { HistoryTabs(showCruise, onSelect = { cruiseTab = it }) }
                    }
                    if (shown.isEmpty()) {
                        item { EmptyHistory(onRefresh) }
                    }
                    items(shown, key = { it.beginEpoch }) { record ->
                        HistoryRow(record, onClick = { selected = record })
                    }
                    if (showCruise && shown.isNotEmpty()) {
                        item {
                            Text(
                                SecondaryStrings.RECENT_CRUISE_TIP,
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
    if (helpOpen && summary != null) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text(SecondaryStrings.WORKING_TIMES) },
            text = { Text(workingTimesHelp(summary.totalCount, summary.patrolCount)) },
            confirmButton = { TextButton(onClick = { helpOpen = false }) { Text(SecondaryStrings.OK) } },
        )
    }
}

internal fun cruiseDetailTitle(record: CleanRecord): String =
    if (record.cleanType == com.kodraliu.localrock.shared.vacuum.CLEAN_TYPE_PET_PATROL) SecondaryStrings.PET_FINDING_DETAILS
    else SecondaryStrings.CRUISE_DETAILS

/** Big number, small unit, caption underneath, three across like the official header. */
@Composable
private fun HistoryHeader(summary: CleanSummary, onHelp: () -> Unit) {
    val (area, areaUnit) = areaParts(totalAreaLabel(summary.totalAreaMm2))
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigStat(area, areaUnit, SecondaryStrings.TOTAL_AREA, modifier = Modifier.weight(1f))
        BigStat("${totalMinutes(summary.totalTimeSec)}", SecondaryStrings.MIN_UNIT, SecondaryStrings.TOTAL_TIME, modifier = Modifier.weight(1f))
        BigStat("${workingTimes(summary)}", SecondaryStrings.TIMES_UNIT, SecondaryStrings.WORKING_TIMES, onHelp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BigStat(
    value: String,
    unit: String,
    caption: String,
    onHelp: (() -> Unit)? = null,
    centred: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    Modifier.padding(bottom = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onHelp != null) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    caption,
                    Modifier.size(16.dp).clip(CircleShape).clickable(onClick = onHelp),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Plain text tabs, selected one bold, as in the official History page. */
@Composable
private fun HistoryTabs(cruise: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(false to SecondaryStrings.TAB_CLEANING, true to SecondaryStrings.TAB_CRUISE).forEach { (isCruise, label) ->
            val on = cruise == isCruise
            Text(
                label,
                Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(isCruise) }.padding(vertical = 6.dp, horizontal = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistory(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(SecondaryStrings.NO_RECORDS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onRefresh) { Text(SecondaryStrings.LOAD_HISTORY) }
    }
}

@Composable
private fun StatusDot(done: Boolean, size: Int = 28) {
    Icon(
        if (done) Icons.Default.CheckCircle else Icons.Default.Error,
        null,
        Modifier.size(size.dp),
        if (done) AppColors.Good else AppColors.Warn,
    )
}

@Composable
private fun HistoryRow(record: CleanRecord, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(record.complete == 1)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(historyStamp(record.beginEpoch), style = MaterialTheme.typography.titleLarge)
                Text(historyRowSubtitle(record), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** Pinned result card with Hide/Details over the timeline, map when collapsed, and the time and area footer. */
@Composable
private fun CleanRecordDetail(
    record: CleanRecord,
    loadMap: suspend (beginEpoch: Long) -> ParsedMap,
    loadTimeline: suspend (CleanRecord) -> List<CleanTimelineStep>?,
    roomName: (Int) -> String?,
    knownRooms: List<ParsedMapRoom>,
    mapRotation: Int,
    dirtMapSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    val done = record.complete == 1
    val tags = cleanRecordTags(record)
    // Opens on the map like the official run screen; Details expands the timeline.
    var expanded by remember(record.beginEpoch) { mutableStateOf(false) }
    var map by remember(record.beginEpoch) { mutableStateOf<ParsedMap?>(null) }
    var failed by remember(record.beginEpoch) { mutableStateOf(false) }
    var dirtMode by remember(record.beginEpoch) { mutableStateOf(false) }
    val dirt = remember(map) { map?.dirtLayer() }
    val dirtToggle = showsDirtMapToggle(dirtMapSupported, dirt)
    val playback = remember(record.beginEpoch) { PathPlayback() }
    val viewport = remember(record.beginEpoch) { LiveMapViewport() }
    val named = remember(map, knownRooms) { map?.withRoomNames(knownRooms) }
    val routeless = remember(named) { named?.withoutRoute() }
    map?.let { RunPlaybackClock(playback, it) }
    var steps by remember(record.beginEpoch) { mutableStateOf<List<CleanTimelineStep>?>(null) }
    var loadingSteps by remember(record.beginEpoch) { mutableStateOf(true) }
    LaunchedEffect(record.beginEpoch) {
        runCatching { loadMap(record.beginEpoch) }.onSuccess { map = it }.onFailure { failed = true }
    }
    LaunchedEffect(record.beginEpoch) {
        steps = runCatching { loadTimeline(record) }.getOrNull()
        loadingSteps = false
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!expanded) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    // The live map's renderer, so the run map is as sharp as the device page instead of a stretched bitmap.
                    dirtToggle && dirtMode -> DirtMapImage(dirt!!, named!!, viewport, mapRotation, Modifier.fillMaxSize().padding(8.dp))
                    map != null -> Box(Modifier.fillMaxSize()) {
                        // While a replay runs, the route and robot come from the overlay as they move.
                        VacuumMapImage(
                            if (playback.active) routeless!! else named!!, emptyList(), onRoomTap = {}, onObstacleTap = {}, is3D = false,
                            yawDegrees = mapRotation.toFloat(), onYawBy = {}, tiltDegrees = 0f, onTiltBy = {},
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            viewportScope = record.beginEpoch,
                            providedMapViewport = viewport,
                        )
                        if (playback.active) {
                            RunPlaybackOverlay(map!!, playback, viewport, mapRotation.toFloat(), Modifier.fillMaxSize().padding(8.dp))
                        }
                    }
                    failed -> Text(SecondaryStrings.MAP_FAILED, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            // Below the map, bottom right, as the official run page places the legend and the switch.
            val replay = !playback.active && !(dirtToggle && dirtMode) && canPlayBack(map)
            if (replay || dirtToggle) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (replay) ReplayButton(onClick = { playback.play() })
                    if (dirtToggle) DirtMapSwitch(dirtMode, onToggle = { playback.stop(); dirtMode = !dirtMode })
                }
            }
        }
        if (playback.active) PlaybackBar(playback)
        if (!playback.active) Surface(
            modifier = if (expanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
                    StatusDot(done, 24)
                    Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(finishReasonLabel(record), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (tags.isNotEmpty()) {
                            Text(tags.joinToString("  |  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (expanded) SecondaryStrings.HIDE else SecondaryStrings.DETAILS, color = MaterialTheme.colorScheme.primary)
                        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (expanded) {
                    HorizontalDivider()
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        when {
                            loadingSteps -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            steps.isNullOrEmpty() -> Text(
                                SecondaryStrings.NO_TIMELINE,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> steps!!.forEachIndexed { i, step ->
                                TimelineNode(step, roomName, first = i == 0, last = i == steps!!.lastIndex)
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.extraLarge) {
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val (time, timeUnit) = areaParts(cleanDurationLabel(record.durationSeconds))
                val (area, areaUnit) = areaParts(cleanAreaLabel(record.shownAreaMm2))
                BigStat(time, timeUnit, SecondaryStrings.CLEANING_TIME, centred = true)
                BigStat(area, areaUnit, SecondaryStrings.CLEANING_AREA, centred = true)
            }
        }
    }
}

private val TimeColumn = 48.dp
private val NodeColumn = 24.dp

/** One official timeline node: big (time, filled circle, bold title, mode chip) or small (hollow dot, text, time). */
@Composable
private fun TimelineNode(step: CleanTimelineStep, roomName: (Int) -> String?, first: Boolean, last: Boolean) {
    val major = CleanTimeline.isMajor(step)
    val line = MaterialTheme.colorScheme.primary
    val unreachable = step is CleanTimelineStep.Room && step.unreachable
    val iconCentre = if (major) 14.dp else 11.dp
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val x = (TimeColumn + NodeColumn / 2).toPx()
            val top = if (first) iconCentre.toPx() else 0f
            val bottom = if (last) iconCentre.toPx() else size.height
            if (bottom > top) drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 1.5.dp.toPx())
        }.padding(bottom = if (major) 16.dp else 12.dp),
    ) {
        Box(Modifier.width(TimeColumn).padding(top = 4.dp)) {
            if (major) {
                Text(timelineClock(step.time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.width(NodeColumn).padding(top = if (major) 4.dp else 6.dp), contentAlignment = Alignment.TopCenter) {
            when {
                unreachable -> NodeIcon(Icons.Default.Cancel, AppColors.Warn)
                major -> NodeIcon(Icons.Default.CheckCircle, line)
                else -> Box(
                    Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(2.dp, line, CircleShape),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val isError = step is CleanTimelineStep.Error
            Text(
                CleanTimelineLabels.title(step, roomName),
                style = if (major) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = if (major) FontWeight.Bold else FontWeight.Normal,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            val settings = when (step) {
                is CleanTimelineStep.Start -> step.settings
                is CleanTimelineStep.Room -> step.settings
                else -> null
            }
            if (settings != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        CleanTimelineLabels.chipParts(settings).joinToString("  |  "),
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val detail = if (step is CleanTimelineStep.Start) null else CleanTimelineLabels.detail(step)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unreachable) AppColors.Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!major) {
                Text(timelineClock(step.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NodeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Icon(icon, null, Modifier.size(20.dp), tint)
    }
}
