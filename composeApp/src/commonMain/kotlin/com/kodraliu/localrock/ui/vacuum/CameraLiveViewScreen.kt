package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import com.kodraliu.localrock.shared.vacuum.camera.CameraCredentialEntry
import com.kodraliu.localrock.shared.webrtc.RtcConnectionState
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.vacuum.camera.CameraVideoTest
import com.kodraliu.localrock.shared.vacuum.camera.CameraVideoQuality
import com.kodraliu.localrock.shared.vacuum.camera.experimentalCameraSupported
import com.kodraliu.localrock.shared.webrtc.liveViewSupported

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CameraLiveViewScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val live = remember(viewModel) { CameraLiveSession(viewModel.repository.session) }
    val entryScope = rememberCoroutineScope()
    val entry = remember(live, entryScope) { CameraCredentialEntry(live::checkCredential, entryScope) }
    val checkingCredential by entry.checking.collectAsState()
    val credentialError by entry.error.collectAsState()
    val state by live.state.collectAsState()
    val peer by live.peer.collectAsState()
    val connection = peer?.connectionState?.collectAsState()?.value
    val ready = connection == RtcConnectionState.CONNECTED &&
        state == CameraLiveSession.State.Streaming
    val readyNow by rememberUpdatedState(ready)
    val motion = remember(viewModel) { CameraMotionController(viewModel.repository.session) { readyNow } }
    var expanded by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(false) }
    var pendingCameraStart by remember { mutableStateOf(false) }
    var exitDialog by remember { mutableStateOf(false) }
    var dockDialog by remember { mutableStateOf(false) }
    var exitNavigates by remember { mutableStateOf(false) }
    val startedActivity by motion.viewerStartedActivity.collectAsState()
    val busy by motion.busy.collectAsState()
    val robotStatus by viewModel.status.collectAsState()
    val docked = robotStatus.state in setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)
    val dockedNow by rememberUpdatedState(docked)
    LaunchedEffect(ready) { if (!ready) motion.release() }
    val diagnostics by live.diagnostics.collectAsState()
    val confirmedQuality by live.quality.collectAsState()
    val qualityBusy by live.qualityBusy.collectAsState()
    val qualityError by live.qualityError.collectAsState()
    val motionError by motion.error.collectAsState()
    val cleanupWarning by live.cleanupWarning.collectAsState()
    // Existing credentials are kept only for this screen's foreground lifetime.
    var pin by remember(viewModel) { mutableStateOf("") }
    var typed by remember(viewModel) { mutableStateOf(false) }
    var selectedTest by remember { mutableStateOf(CameraVideoTest.OEM_MATCH) }
    var preferredQuality by remember { mutableStateOf(CameraVideoQuality.HD) }
    var showConnectionTests by remember { mutableStateOf(false) }
    val supported = liveViewSupported && experimentalCameraSupported(viewModel.modelName)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissKeyboard() { focus.clearFocus(); keyboard?.hide() }
    fun start(test: CameraVideoTest = selectedTest, quality: CameraVideoQuality = preferredQuality) {
        if (pin.isNotBlank() && (state == CameraLiveSession.State.Idle || state is CameraLiveSession.State.Failed)) {
            selectedTest = test
            preferredQuality = quality
            dismissKeyboard()
            viewing = true
            pendingCameraStart = dockedNow
            if (!dockedNow) live.start(pin, test, quality)
        }
    }
    fun submitEntry(test: CameraVideoTest = CameraVideoTest.OEM_MATCH, quality: CameraVideoQuality = preferredQuality) {
        if (!supported || viewing) return
        val credential = pin
        dismissKeyboard()
        entry.submit(credential) {
            pin = credential
            start(test, quality)
        }
    }

    LaunchedEffect(confirmedQuality, state) {
        if (state == CameraLiveSession.State.Streaming) preferredQuality = confirmedQuality
    }
    LaunchedEffect(viewing, docked, state) {
        if (viewing && pendingCameraStart && !docked && pin.isNotBlank() && (state == CameraLiveSession.State.Idle || state is CameraLiveSession.State.Failed)) {
            pendingCameraStart = false
            live.start(pin, selectedTest, preferredQuality)
        }
    }
    fun finish(returnToDock: Boolean = false) {
        motion.endViewing(returnToDock=returnToDock) {
            pendingCameraStart = false
            viewing = false
            expanded = false
            exitDialog = false
            dockDialog = false
            live.stop()
            if (exitNavigates) onBack()
        }
    }
    fun requestExit(navigate: Boolean) {
        entry.cancel()
        dismissKeyboard()
        motion.release()
        exitNavigates = navigate
        if (!viewing) { live.stop(); if (navigate) onBack() }
        else if (startedActivity) exitDialog = true
        else finish()
    }
    BackHandler {
        if (expanded) {motion.release(); expanded=false}
        else requestExit(true)
    }
    DisposableEffect(live, motion, entry) { onDispose { entry.cancel(); motion.dispose(); live.dispose() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        motion.release()
        live.voice.muteMicrophone(true)
    }
    // A microphone permission prompt can pause the app without backgrounding it.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        entry.cancel()
        pin = ""
        viewing = false
        pendingCameraStart = false
        exitDialog = false
        dockDialog = false
        expanded = false
        motion.background()
        live.stop()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Black,
        contentColor = Color.White,
        contentWindowInsets = if (expanded) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        topBar = {
            if (!expanded) TopAppBar(
                title = { Text(if (viewing) "Remote viewing · ${selectedTest.code}" else "Remote viewing", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { requestExit(true) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Go back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            if (supported && !viewing && state == CameraLiveSession.State.Idle) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    if (typed) TextButton(onClick = { dismissKeyboard() }) { Text("Hide keyboard", color = Color.White) }
                    Text(if (typed) "Enter your PIN, then open video." else "Draw your pattern to open video.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
            // Attach the renderer while connecting so a first video frame can be decoded.
            if (viewing) {
                CameraVideoControls(viewModel, peer, motion, state, ready, expanded,
                    voice=live.voice,
                    onExpand={expanded=it}, onStopVideo={requestExit(false)},
                    onDock={motion.release();exitNavigates=false;dockDialog=true},onRetry={start()},
                    quality=if(pendingCameraStart || state is CameraLiveSession.State.Failed) preferredQuality else confirmedQuality,
                    qualityEnabled=!qualityBusy && (pendingCameraStart || state==CameraLiveSession.State.Streaming || state is CameraLiveSession.State.Failed),
                    qualityError=qualityError, diagnostics=diagnostics,
                    onQuality={quality->
                        if (state==CameraLiveSession.State.Streaming) live.changeQuality(quality)
                        else {preferredQuality=quality;if(!pendingCameraStart) start(quality=quality)}
                    },onRetrySd={start(quality=CameraVideoQuality.SD)})
            }
            if (!supported) {
                Text("Experimental video is currently available for S7 MaxV only, in builds with WebRTC enabled.",
                    Modifier.padding(24.dp), textAlign = TextAlign.Center)
            } else if (!viewing) when (val s = state) {
                CameraLiveSession.State.Idle -> Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(viewModel.deviceName, style = MaterialTheme.typography.titleMedium)
                    Text("While docked, open video, choose a spot on the map, and tap Go to move the robot.", textAlign = TextAlign.Center)
                    CameraCleaningButton(viewModel, motion)
                    motionError?.let { Text(it, color = Color.Yellow, textAlign = TextAlign.Center) }
                    Text("Enter your existing camera PIN or pattern. Activate remote viewing in the official Roborock app before switching to the local server.",
                        textAlign = TextAlign.Center)
                    if (typed) {
                        OutlinedTextField(
                            value = pin, onValueChange = { pin = it.filter { c -> c in '0'..'9' } }, label = { Text("Existing camera PIN") },
                            singleLine = true, enabled = !checkingCredential, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitEntry() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedLabelColor = Color.White, unfocusedLabelColor = Color.LightGray,
                            ),
                        )
                    } else if (!checkingCredential) {
                        PatternPinInput(onPattern = {
                            pin = it
                            if (it.isNotBlank() && !showConnectionTests) submitEntry()
                        })
                        Text(if (showConnectionTests) "Draw your pattern, then choose a connection test" else "Draw your existing pattern to open video")
                    }
                    if (checkingCredential) {
                        CircularProgressIndicator(color = Color.White)
                        Text("Checking camera credential…")
                        TextButton(onClick = { entry.cancel(); pin = "" }) { Text("Cancel", color = Color.White) }
                    }
                    credentialError?.let { Text(it, color = Color.Yellow, textAlign = TextAlign.Center) }
                    TextButton(onClick = { entry.cancel(); dismissKeyboard(); typed = !typed; pin = "" }) {
                        Text(if (typed) "Use a pattern" else "Use a numeric PIN", color = Color.White)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        CameraVideoQuality.entries.forEach {quality->
                            FilterChip(selected=preferredQuality==quality,onClick={preferredQuality=quality},enabled=!checkingCredential,label={Text(quality.name)})
                        }
                    }
                    if (typed || showConnectionTests) Button(onClick={submitEntry()},enabled=pin.isNotBlank() && !checkingCredential) {Text("Open video")}
                    TextButton(onClick={showConnectionTests=!showConnectionTests},enabled=!checkingCredential) {Text("Connection tests (SD)")}
                    if(showConnectionTests) CameraTestTiles(enabled=pin.isNotBlank() && !checkingCredential,onStart={submitEntry(it,CameraVideoQuality.SD)})
                    Text("Video does not use your phone's camera. Your microphone is used only during a voice call; audio is not saved.",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    cleanupWarning?.let { Text(it, color = Color.Yellow, textAlign = TextAlign.Center) }
                }
                is CameraLiveSession.State.Connecting -> if (!viewing) Column(
                    Modifier.background(Color.Black.copy(alpha = .8f)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(s.stage)
                    Text(diagnostics.summary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { live.stop() }) { Text("Cancel", color = Color.White) }
                }
                CameraLiveSession.State.Streaming -> Unit
                CameraLiveSession.State.Stopping -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Releasing camera…", Modifier.padding(16.dp))
                }
                is CameraLiveSession.State.Failed -> Column(
                    Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(s.message, textAlign = TextAlign.Center)
                    CameraCleaningButton(viewModel, motion)
                    motionError?.let { Text(it, color = Color.Yellow, textAlign = TextAlign.Center) }
                    cleanupWarning?.let { Text(it, color = Color.Yellow, textAlign = TextAlign.Center) }
                    Button(onClick = { start() }, enabled = pin.isNotBlank()) { Text("Retry Video ${selectedTest.code}") }
                    TextButton(onClick = { live.stop() }) { Text("Choose another video test", color = Color.White) }
                    TextButton(onClick = { pin = ""; live.stop() }) { Text("Enter PIN again", color = Color.White) }
                }
            }
        }
    }
    if (exitDialog || dockDialog) AlertDialog(
        onDismissRequest={if(!busy) {exitDialog=false;dockDialog=false}},
        title={Text("End video and return to dock?")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Returning to dock ends the current movement or cleaning task.")
            motionError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            if (exitDialog) TextButton(onClick={finish()},enabled=!busy) {Text("End video only · keep cleaning")}
        }},
        confirmButton={TextButton(onClick={finish(returnToDock=true)},enabled=!busy) {Text("Return to dock")}},
        dismissButton={TextButton(onClick={exitDialog=false;dockDialog=false},enabled=!busy) {Text("Cancel")}},
    )

}

@Composable
private fun CameraTestTiles(enabled: Boolean, onStart: (CameraVideoTest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CameraVideoTest.entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { test ->
                    OutlinedCard(onClick = { onStart(test) }, enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 132.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = Color(0xff18232d), contentColor = Color.White,
                            disabledContainerColor = Color(0xff18232d), disabledContentColor = Color.LightGray)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Video ${test.code}", style = MaterialTheme.typography.labelLarge)
                            Text(test.label, style = MaterialTheme.typography.titleSmall)
                            Text(test.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}


