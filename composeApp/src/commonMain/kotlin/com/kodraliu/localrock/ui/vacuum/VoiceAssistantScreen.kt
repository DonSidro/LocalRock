package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.VoiceInstallFailure
import com.kodraliu.localrock.shared.vacuum.VoiceInstallState
import com.kodraliu.localrock.shared.vacuum.VoicePack
import com.kodraliu.localrock.shared.vacuum.VoicePackListState
import com.kodraliu.localrock.shared.vacuum.VolumeLevels
import com.kodraliu.localrock.shared.vacuum.formatVoiceTimestamp
import com.kodraliu.localrock.shared.vacuum.volumeLabel
import com.kodraliu.localrock.shared.vacuum.volumeLevelIndex
import com.kodraliu.localrock.shared.vacuum.voiceLanguageName
import com.kodraliu.localrock.shared.vacuum.voiceTimestampsShown
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.strings.VoiceCommandStrings
import com.kodraliu.localrock.ui.strings.VoiceStrings
import kotlinx.coroutines.launch

private enum class VoicePage { MAIN, COMMANDS, HISTORY, LANGUAGE }

/** The official Voice Assistant page and the pages it opens, reached from Robot settings on voice control robots. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VoiceAssistantSettings(viewModel: VacuumViewModel, onBack: () -> Unit) {
    var page by remember { mutableStateOf(VoicePage.MAIN) }
    // Registered after the Settings handler, so system back steps through these pages first.
    BackHandler { if (page == VoicePage.MAIN) onBack() else page = VoicePage.MAIN }
    LaunchedEffect(Unit) {
        runCatching { viewModel.voice.fetchSwitch() }
        runCatching { viewModel.voice.fetchSettings() }
        runCatching { viewModel.repository.fetchRobotSettings() }
    }
    val toMain = { page = VoicePage.MAIN }
    when (page) {
        VoicePage.MAIN -> VoiceAssistantScreen(viewModel, onBack = onBack, onOpen = { page = it })
        VoicePage.COMMANDS -> VoiceCommandsScreen(onBack = toMain)
        VoicePage.HISTORY -> VoiceHistoryScreen(viewModel, onBack = toMain)
        VoicePage.LANGUAGE -> VoiceLanguageScreen(viewModel, onBack = toMain)
    }
}

@Composable
private fun VoiceAssistantScreen(viewModel: VacuumViewModel, onBack: () -> Unit, onOpen: (VoicePage) -> Unit) {
    val switch by viewModel.voice.switch.collectAsState()
    val voice by viewModel.voice.settings.collectAsState()
    val robotSettings by viewModel.repository.robotSettings.collectAsState()
    var showVolumeDialog by remember { mutableStateOf(false) }

    VoicePageScaffold(VoiceStrings.VOICE_ASSISTANT, onBack) {
        switch?.let { s ->
            item {
                SettingRowSwitch(
                    icon = Icons.Default.Mic,
                    iconTint = AppColors.AccentCyan,
                    title = VoiceStrings.VOICE_ASSISTANT,
                    subtitle = VoiceStrings.VOICE_ASSISTANT_DETAIL,
                    checked = s.enabled,
                    onCheckedChange = { on -> viewModel.report { viewModel.voice.setSwitch(on) } },
                )
            }
        }
        item { VoiceExamples(onMore = { onOpen(VoicePage.COMMANDS) }) }
        item { Footnote(VoiceStrings.VOICE_POLICY) }
        item { HorizontalDivider(Modifier.padding(top = 8.dp)) }
        voice.languageCode?.let { code ->
            item {
                SettingRowClickable(
                    icon = Icons.Default.Language,
                    iconTint = AppColors.AccentPurple,
                    title = VoiceStrings.LANGUAGE,
                    subtitle = voiceLanguageName(code),
                    onClick = { onOpen(VoicePage.LANGUAGE) },
                )
            }
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
        }
        item {
            SettingRowClickable(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconTint = AppColors.Water,
                title = VoiceStrings.VOLUME,
                subtitle = volumeLabel(robotSettings.volume),
                onClick = { showVolumeDialog = true },
            )
        }
        if (voice.summary != null) {
            item { HorizontalDivider(Modifier.padding(start = 72.dp)) }
            item {
                SettingRowClickable(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    iconTint = AppColors.AccentIndigo,
                    title = VoiceStrings.CHAT_HISTORY,
                    subtitle = VoiceStrings.CHAT_HISTORY_DETAIL,
                    onClick = { onOpen(VoicePage.HISTORY) },
                )
            }
        }
    }

    if (showVolumeDialog) {
        SimplePickerDialog(
            title = VoiceStrings.VOLUME,
            options = VolumeLevels.map { it.second },
            selected = volumeLevelIndex(robotSettings.volume),
            onSelect = { i -> viewModel.report { it.setVolume(VolumeLevels[i].first) }; showVolumeDialog = false },
            onDismiss = { showVolumeDialog = false },
        )
    }
}

/** "Say Hello Rocky", two rows of example commands and the link to the full list, as on the official card. */
@Composable
private fun VoiceExamples(onMore: () -> Unit) {
    // The official page shuffles every phrase, keeps 16 and splits them over two rows.
    val chips = remember { VoiceCommandStrings.ALL_PHRASES.map { it.trim() }.shuffled().take(16) }
    val half = (chips.size + 1) / 2
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WakePhrase(VoiceStrings.VOICE_SAY, Modifier.padding(horizontal = 16.dp))
        listOf(chips.take(half), chips.drop(half)).forEach { row ->
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { row.forEach { VoiceCommandChip(it) } }
        }
        Surface(onClick = onMore, color = Color.Transparent, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(VoiceStrings.VOICE_MORE_INSTRUCTIONS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** [lead] followed by the wake phrase in the accent colour. */
@Composable
private fun WakePhrase(lead: String, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        buildAnnotatedString {
            append("$lead ")
            withStyle(SpanStyle(color = accent)) { append(VoiceStrings.VOICE_WAKE_PHRASE) }
        },
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

/** Voice Commands: the official example list, one section per group. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceCommandsScreen(onBack: () -> Unit) {
    VoicePageScaffold(VoiceStrings.VOICE_COMMANDS, onBack) {
        item { WakePhrase(VoiceStrings.VOICE_WAKE_AND_SAY, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        VoiceCommandStrings.GROUPS.forEach { group ->
            item(key = "caption-${group.title}") {
                Text(
                    group.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            }
            group.commands.forEachIndexed { i, command ->
                item(key = "${group.title}-$i") {
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(command.title, style = MaterialTheme.typography.bodyLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            command.phrases.forEach { VoiceCommandChip(it.trim()) }
                        }
                    }
                }
            }
        }
    }
}

/** Chat History: the robot's voice log, oldest at the top and opening on the newest, as the official list does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceHistoryScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val voice by viewModel.voice.settings.collectAsState()
    LaunchedEffect(Unit) { runCatching { viewModel.voice.fetchSettings(withHistory = true) } }
    val messages = voice.history
    val stamps = remember(messages) { voiceTimestampsShown(messages) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VoiceStrings.CHAT_HISTORY) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
            if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(VoiceStrings.CHAT_HISTORY_EMPTY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Newest first in a reversed list, so it opens at the bottom like a chat.
                val newestFirst = remember(messages) { messages.indices.reversed().toList() }
                LazyColumn(
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 12.dp),
                ) {
                    itemsIndexed(newestFirst, key = { _, i -> messages[i].id }) { _, i ->
                        val m = messages[i]
                        val stamp = if (stamps[i]) formatVoiceTimestamp(m.ts, localUtcOffsetSeconds(m.ts)) else null
                        VoiceChatBubble(m.text, m.fromUser, stamp)
                    }
                }
            }
        }
    }
}

/** A voice page: back arrow, title and a list, laid out like the other settings pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        CenteredContent {
            LazyColumn(contentPadding = padding, content = content)
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** An outlined pill holding one example voice command. */
@Composable
private fun VoiceCommandChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** One voice chat line: the user's on the right, the robot's on the left, with an optional stamp above. */
@Composable
private fun VoiceChatBubble(text: String, fromUser: Boolean, timestamp: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (timestamp != null) {
            Text(
                timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.padding(start = if (fromUser) 104.dp else 0.dp, end = if (fromUser) 0.dp else 104.dp),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Language: the robot's voice packs with Current or Use, as the official SoundPackagePage lists them. */
@Composable
private fun VoiceLanguageScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val packs by viewModel.voicePacks.collectAsState()
    val install by viewModel.voiceInstall.collectAsState()
    val current by viewModel.voice.currentSound.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.loadVoicePacks() }
    LaunchedEffect(install?.failure) {
        val failure = install?.failure ?: return@LaunchedEffect
        snackbar.showSnackbar(voiceInstallFailureText(failure))
        viewModel.dismissVoiceInstallFailure()
    }
    VoicePageScaffold(VoiceStrings.LANGUAGE, onBack, snackbar) {
        when (val state = packs) {
            VoicePackListState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is VoicePackListState.Unavailable -> item {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(VoiceStrings.VOICE_PACKS_UNAVAILABLE, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.reason ?: VoiceStrings.VOICE_PACKS_UNAVAILABLE_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.loadVoicePacks() }) { Text(VoiceStrings.RETRY) }
                }
            }
            is VoicePackListState.Loaded -> {
                if (state.packs.isEmpty()) {
                    item { Footnote(VoiceStrings.VOICE_PACKS_EMPTY) }
                } else {
                    itemsIndexed(state.packs) { i, pack ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        VoicePackRow(pack, current?.sidInUse == pack.id, install) {
                            if (install?.running == true) {
                                scope.launch { snackbar.showSnackbar(VoiceStrings.VOICE_PACK_BUSY) }
                            } else {
                                viewModel.installVoicePack(pack)
                            }
                        }
                    }
                    if (install?.running == true) item { Footnote(VoiceStrings.VOICE_PACK_DOWNLOADING) }
                }
            }
        }
    }
}

@Composable
private fun VoicePackRow(pack: VoicePack, isCurrent: Boolean, install: VoiceInstallState?, onUse: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pack.title, style = MaterialTheme.typography.bodyLarge)
            pack.subTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        when {
            install?.running == true && install.sid == pack.id -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                install.percent?.let { Text("$it%", style = MaterialTheme.typography.bodyMedium) }
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            isCurrent -> Text(VoiceStrings.VOICE_CURRENT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> OutlinedButton(onClick = onUse) { Text(VoiceStrings.VOICE_USE) }
        }
    }
}

private fun voiceInstallFailureText(failure: VoiceInstallFailure): String = when (failure) {
    VoiceInstallFailure.SEND -> VoiceStrings.VOICE_PACK_SEND_FAILED
    VoiceInstallFailure.DOWNLOAD -> VoiceStrings.VOICE_PACK_DOWNLOAD_FAILED
    VoiceInstallFailure.INSTALL -> VoiceStrings.VOICE_PACK_INSTALL_FAILED
    VoiceInstallFailure.TIMEOUT -> VoiceStrings.VOICE_PACK_TIMEOUT
}
