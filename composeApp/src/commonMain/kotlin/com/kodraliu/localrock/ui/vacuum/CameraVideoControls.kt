package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.vacuum.camera.CameraVideoQuality
import com.kodraliu.localrock.shared.vacuum.camera.CameraMotionController
import com.kodraliu.localrock.shared.vacuum.camera.CameraVoiceCall
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.webrtc.RtcPeer
import com.kodraliu.localrock.shared.webrtc.RtcDiagnostics
import com.kodraliu.localrock.shared.webrtc.RtcVideoView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CameraVideoControls(
    viewModel: VacuumViewModel, peer: RtcPeer?, motion: CameraMotionController,
    state: CameraLiveSession.State, ready: Boolean, expanded: Boolean,
    voice: CameraVoiceCall,
    onExpand: (Boolean) -> Unit, onStopVideo: () -> Unit, onDock: () -> Unit,
    onRetry: () -> Unit, quality: CameraVideoQuality, qualityEnabled: Boolean, qualityError: String?,
    diagnostics: RtcDiagnostics, onQuality: (CameraVideoQuality) -> Unit, onRetrySd: () -> Unit,
) {
    val status by viewModel.status.collectAsState()
    val map by viewModel.repository.parsedMap.collectAsState()
    val floor by viewModel.repository.currentFloorFlag.collectAsState()
    val busy by motion.busy.collectAsState()
    val error by motion.error.collectAsState()
    val controlProgress by motion.progress.collectAsState()
    val callActive by voice.active.collectAsState()
    val callBusy by voice.busy.collectAsState()
    val callError by voice.error.collectAsState()
    val microphoneMuted by voice.microphoneMuted.collectAsState()
    val remoteAudioMuted by voice.remoteAudioMuted.collectAsState()
    val speakerVolume by voice.speakerVolume.collectAsState()
    val volumeBusy by voice.volumeBusy.collectAsState()
    val callsSupported = peer?.supportsVoiceCall == true
    var steering by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var fillVideo by remember { mutableStateOf(true) }
    var mapExpanded by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var target by remember(floor, map?.viewportIdentity(floor)) { mutableStateOf<Pair<Int,Int>?>(null) }
    // Navigation is independent of video: a docked robot must be able to leave through Go.
    val enabled = !busy
    val docked = status.state in setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)
    val headline = if (status.state == VacuumStateCodes.CHARGING_COMPLETE) "Charged"
        else status.state?.let { VacuumStateCodes.describe(it) } ?: "Robot status unavailable"
    fun interact() { controlsVisible = true; interaction++ }
    val callLabel = if (callBusy) "Cancel call" else if (callActive) "End call" else "Start call"
    val callEnabled = callActive || callBusy || (ready && callsSupported)
    val toggleCall: () -> Unit = {
        motion.release()
        if (callActive || callBusy) voice.stop() else voice.start()
    }
    val callButton: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalButton(onClick=toggleCall,enabled=callEnabled,modifier=modifier,
            colors=ButtonDefaults.filledTonalButtonColors(
                containerColor=if(callActive) Color(0xff168658) else Color(0xff303030),contentColor=Color.White)) {
            Icon(if(callActive || callBusy) Icons.Default.CallEnd else Icons.Default.Mic,null)
            Spacer(Modifier.width(6.dp))
            Text(callLabel)
        }
    }
    val callIconButton: @Composable (Modifier) -> Unit = { modifier ->
        FilledTonalIconButton(onClick=toggleCall,enabled=callEnabled,modifier=modifier.size(56.dp),
            colors=IconButtonDefaults.filledTonalIconButtonColors(
                containerColor=if(callActive || callBusy) Color(0xffb3261e) else Color(0xff168658),
                contentColor=Color.White)) {
            Icon(if(callActive || callBusy) Icons.Default.CallEnd else Icons.Default.Mic,callLabel)
        }
    }
    LaunchedEffect(expanded, interaction, busy, mapExpanded, settings) {
        controlsVisible = true
        if (expanded && !busy && !mapExpanded && !settings) {
            delay(5_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(expanded) { if (!expanded) mapExpanded = false }
    LaunchedEffect(viewModel, ready, status.state) {
        if (ready && status.state == VacuumStateCodes.REMOTE_CONTROL_ACTIVE) {
            while (isActive) {
                try { viewModel.repository.fetchMap() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Keep the last map while video remains usable. */ }
                delay(3_000)
            }
        }
    }
    fun go() {
        val point = target ?: return
        val current = map ?: return
        val cell = current.mmToCell(point.first,point.second)
        if (cameraTargetAt(current,Offset(cell.x/current.width,cell.y/current.height)) != null) {
            motion.goTo(point.first,point.second,floor)
            mapExpanded = false
        } else target = null
    }
    val targetPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, canClose ->
        Column(modifier.background(Color(0xffe9edf1))) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Tap the map to set a target",Modifier.weight(1f),color=Color.DarkGray)
                if (canClose) IconButton(onClick={mapExpanded=false;interact()}) { Icon(Icons.Default.Close,"Close expanded map",tint=Color.Black) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val pm=map
                if (pm==null) Text("Waiting for the map…",color=Color.DarkGray)
                else CameraTargetMap(pm,floor,target,enabled,{target=it},Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.End) {
                Button(onClick={go()},enabled=enabled && target!=null) {
                    Icon(Icons.Default.NearMe,null);Spacer(Modifier.width(6.dp));Text("Go")
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        // Observe interaction without consuming map gestures or hold-to-drive pointer events.
        awaitPointerEventScope {
            while (true) {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any {it.pressed}) interact()
            }
        }
    }) {
        val landscape = maxWidth > maxHeight
        val wide = expanded && landscape
        val overlayInsets = if (expanded) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0)
        val portraitVideoHeight = maxHeight * .43f
        val videoModifier = if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(portraitVideoHeight)
        // Keep one native renderer composition site through expansion, rotation and map changes.
        Box(videoModifier.background(Color.Black).clipToBounds()) {
            peer?.let { currentPeer -> key(currentPeer) { RtcVideoView(currentPeer,Modifier.fillMaxSize(),fill=wide && fillVideo) } }
            if (docked) {
                Text("Robot docked. Choose a spot on the map and tap Go to move it and start video.",
                    Modifier.align(Alignment.Center).padding(horizontal=32.dp,vertical=72.dp),
                    color=Color.White,textAlign=TextAlign.Center)
            } else when (val current=state) {
                is CameraLiveSession.State.Connecting -> Text(current.stage,
                    Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=.65f)).padding(12.dp),color=Color.White)
                is CameraLiveSession.State.Failed -> Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(current.message,color=Color.White,textAlign=TextAlign.Center)
                    Button(onClick=onRetry) { Text("Retry video") }
                    if(quality==CameraVideoQuality.HD) TextButton(onClick=onRetrySd) {Text("Retry in SD")}
                }
                else -> Unit
            }
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .background(Color.Black.copy(alpha=.55f)).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(status.battery?.let {"$it%"} ?: "—%",Modifier.padding(horizontal=8.dp),color=Color.White)
                Text(headline,Modifier.weight(1f),color=Color.White,textAlign=TextAlign.Center,maxLines=1,overflow=TextOverflow.Ellipsis)
                if (controlsVisible || !expanded) {
                    TextButton(onClick={motion.release();settings=true}) { Text(quality.name,color=Color.White) }
                }
                // Keep the exit reachable even after the driving overlays fade.
                if (expanded) {
                    TextButton(onClick={motion.release();onExpand(false)}) {
                        Icon(Icons.Default.FullscreenExit,null,tint=Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Exit",color=Color.White)
                    }
                } else IconButton(onClick={motion.release();onExpand(true)}) {
                    Icon(Icons.Default.Fullscreen,"Expand video",tint=Color.White)
                }
                if (controlsVisible || !expanded) {
                    IconButton(onClick={motion.release();settings=true}) { Icon(Icons.Default.MoreVert,"Video settings",tint=Color.White) }
                }
            }
            if (!expanded) CameraCleaningButton(viewModel,motion,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (!expanded) callIconButton(Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        if (wide) {
            if (mapExpanded) {
                targetPanel(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(.6f)
                    .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)).padding(top=48.dp),true)
            } else {
                Box(Modifier.align(Alignment.TopStart).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(top=56.dp,start=12.dp)
                    .size((maxHeight * .30f).coerceAtMost(150.dp))) {
                    map?.let {pm->CameraTargetMap(pm,floor,null,false,{},Modifier.fillMaxSize())}
                    // The overlay owns the tap; the thumbnail itself must not pan behind it.
                    Box(Modifier.fillMaxSize().semantics { contentDescription="Expand map for Pin-and-Go";onClick {motion.release();mapExpanded=true;true} }
                        .pointerInput(Unit) {detectTapGestures(onTap={motion.release();mapExpanded=true})})
                }
                if (controlsVisible) CameraDrivePad(motion,ready,
                    Modifier.align(Alignment.BottomStart).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                        .padding(16.dp).size((maxHeight*.48f).coerceAtMost(220.dp)))
            }
            // Keep call controls in the right rail, outside the expanded map and its Go button.
            Column(Modifier.align(Alignment.BottomEnd)
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if (controlsVisible) {
                    CameraCleaningButton(viewModel,motion,compact=true)
                    FilledTonalIconButton(onClick=onDock,enabled=enabled,modifier=Modifier.size(56.dp)) { Icon(Icons.Default.Home,"Return to dock and end video") }
                }
                callIconButton(Modifier)
            }
        } else if (expanded) {
            if (controlsVisible) Text("Turn your phone sideways for full-screen controls.",
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom))
                    .padding(bottom=64.dp).fillMaxWidth().background(Color.Black.copy(alpha=.6f)).padding(16.dp),color=Color.White)
        } else {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(portraitVideoHeight))
                Row(Modifier.fillMaxWidth().background(Color(0xff242424)).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={motion.release();steering=!steering}) { Text(if(steering) "Map" else "Controls",color=Color.White) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={motion.stop()}) { Icon(Icons.Default.Stop,"Stop movement",tint=Color.White) }
                    IconButton(onClick=onDock,enabled=enabled) { Icon(Icons.Default.Home,"Return to dock and end video",tint=if(enabled) Color.White else Color.Gray) }
                    TextButton(onClick=onStopVideo) { Text("End video",color=Color.White) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0xff181818)),contentAlignment=Alignment.Center) {
                    if (steering) {
                        val padSize=minOf(maxWidth*.85f,(maxHeight-40.dp).coerceAtLeast(48.dp))
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            CameraDrivePad(motion,ready,Modifier.size(padSize))
                            Text("Hold to drive · release to stop",color=Color.LightGray,modifier=Modifier.padding(8.dp))
                        }
                    } else targetPanel(Modifier.fillMaxSize(),false)
                }
            }
        }
        if (expanded && !wide) {
            callIconButton(Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(bottom=12.dp))
        }
        (error ?: callError ?: controlProgress)?.let {Text(it,Modifier.align(Alignment.TopCenter)
            .windowInsetsPadding(overlayInsets.only(WindowInsetsSides.Top)).padding(top=52.dp)
            .background(if (error != null || callError != null) Color(0xff5b2424) else Color(0xff242424))
            .padding(8.dp),color=Color.White)}
    }
    if(settings) AlertDialog(
        onDismissRequest={settings=false},title={Text("Remote viewing")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Requested video quality")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CameraVideoQuality.entries.forEach {value->
                    FilterChip(selected=quality==value,onClick={onQuality(value)},enabled=qualityEnabled,label={Text(value.name)})
                }
            }
            if(diagnostics.videoWidth>0 && diagnostics.videoHeight>0)
                Text("Received video: ${diagnostics.videoWidth} × ${diagnostics.videoHeight}")
            qualityError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Text("Landscape video framing")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilterChip(selected=!fillVideo,onClick={fillVideo=false},label={Text("Fit")})
                FilterChip(selected=fillVideo,onClick={fillVideo=true},label={Text("Fill")})
            }
            Text("Fit shows the whole camera frame. Fill trims edges to cover the screen.")
            if (callsSupported) {
                callButton(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Microphone",Modifier.weight(1f))
                    Switch(checked=callActive && !microphoneMuted,onCheckedChange={voice.muteMicrophone(!it)},enabled=callActive)
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("Robot audio",Modifier.weight(1f))
                    Switch(checked=callActive && !remoteAudioMuted,onCheckedChange={voice.muteRemoteAudio(!it)},enabled=callActive)
                }
                Text("Robot speaker volume" + (speakerVolume?.let { ": $it%" } ?: ": unchanged"))
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(25,50,75).forEach {value->
                        FilterChip(selected=speakerVolume==value,onClick={voice.setSpeakerVolume(value)},
                            enabled=callActive && !volumeBusy,label={Text("$value%")})
                    }
                }
                Text("Use your phone's volume buttons for the sound you hear. Calls are experimental and audio is not saved.")
            } else Text("Voice calls are available in the iPhone test build once video connects.")
            callError?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()) {Text("Activate Remote Viewing")}
            Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()) {Text("Deactivate Remote Viewing")}
            Text("Activate remote viewing in the official Roborock app while connected to Roborock's servers, before switching to the local server. Activation controls are unavailable in LocalRock.")
        }},
        confirmButton={TextButton(onClick={settings=false}){Text("Done")}},
    )
}

@Composable
private fun CameraDrivePad(motion: CameraMotionController, enabled: Boolean, modifier: Modifier) {
    val scope=rememberCoroutineScope()
    Box(modifier.clip(CircleShape).background(Color(0xff242424).copy(alpha=.55f))) {
        val keys=listOf(
            Triple(CameraMotionController.Direction.FORWARD,Alignment.TopCenter,Icons.Default.KeyboardArrowUp),
            Triple(CameraMotionController.Direction.BACKWARD,Alignment.BottomCenter,Icons.Default.KeyboardArrowDown),
            Triple(CameraMotionController.Direction.LEFT,Alignment.CenterStart,Icons.Default.RotateLeft),
            Triple(CameraMotionController.Direction.RIGHT,Alignment.CenterEnd,Icons.Default.RotateRight),
        )
        keys.forEach { (direction,align,icon) ->
            val label=when(direction){
                CameraMotionController.Direction.FORWARD -> "Drive forward"
                CameraMotionController.Direction.BACKWARD -> "Drive backward"
                CameraMotionController.Direction.LEFT -> "Turn left"
                CameraMotionController.Direction.RIGHT -> "Turn right"
            }
            Box(Modifier.align(align).fillMaxSize(.32f)
                .semantics { contentDescription=label; onClick {
                    if(enabled) scope.launch { motion.hold(direction);delay(250);motion.release() }
                    enabled
                } }
                .pointerInput(enabled,direction) {
                    if(enabled) detectTapGestures(onPress={
                        motion.hold(direction)
                        try { tryAwaitRelease() } finally { motion.release() }
                    })
                },contentAlignment=Alignment.Center) {
                Icon(icon,label,Modifier.size(34.dp),tint=if(enabled) Color.White else Color.Gray)
            }
        }
        FilledTonalIconButton(onClick={motion.stop()},modifier=Modifier.align(Alignment.Center).fillMaxSize(.27f)) {
            Icon(Icons.Default.Stop,"Stop movement")
        }
    }
}

/** Only actual floor cells are eligible, with the same Y inversion as the normal map. */
internal fun cameraTargetAt(map: ParsedMap, normalized: Offset): Pair<Int,Int>? {
    if(map.format!=MapFormat.LEGACY || !normalized.x.isFinite() || !normalized.y.isFinite()) return null
    if(normalized.x !in 0f..<1f || normalized.y !in 0f..<1f) return null
    val x=(normalized.x*map.width).toInt()
    val y=map.height-1-(normalized.y*map.height).toInt()
    val cell=map.grid.getOrNull(y*map.width+x)?.toInt()?.and(255) ?: return null
    val original = map.originalGrid?.getOrNull(y*map.width+x)?.toInt()?.and(255)
    val scannedRoom = if (original != null) original and 7 == 7 && original ushr 3 > 0 else cell in 1..126
    if (cell == 127 || (cell != 128 && !scannedRoom)) return null
    val point=map.cellToMm(Offset(normalized.x*map.width,normalized.y*map.height))
    if(point.first !in 0..51_200 || point.second !in 0..51_200) return null
    if(map.noGoZones.any { it.contains(point.first,point.second) }) return null
    return point
}

@Composable
private fun CameraTargetMap(map: ParsedMap,floor:Int,target:Pair<Int,Int>?,enabled:Boolean,onTarget:(Pair<Int,Int>)->Unit,modifier:Modifier) {
    val bitmap=rememberMapBitmap(map)
    val viewport=remember { LiveMapViewport() }
    var measured by remember { mutableStateOf(Size.Zero) }
    val identity=map.viewportIdentity(floor)
    val fit=remember(map,measured) { if(measured.width>0f && measured.height>0f) frameMap(map.contentBoundsNorm(),map.fittedSize(measured),measured) else null }
    LaunchedEffect(identity,measured,fit) { viewport.frameIfNeeded(identity,measured,fit) }
    val currentMap by rememberUpdatedState(map)
    val selected by rememberUpdatedState(onTarget)
    Canvas(modifier.clipToBounds().onSizeChanged {measured=it.toSize()}
        .pointerInput(identity) {detectTransformGestures {center,pan,zoom,_->
            val next=zoomMap(viewport.scale,viewport.offset,center,pan,zoom,currentMap.fittedSize(size.toSize()),size.toSize())
            viewport.scale=next.first;viewport.offset=next.second
        }}
        .pointerInput(identity,enabled) {if(enabled) detectTapGestures(onTap={p->
            val content=currentMap.fittedSize(size.toSize())
            if(content.width>0f && content.height>0f) {
                val local=(p-viewport.offset)/viewport.scale
                cameraTargetAt(currentMap,Offset(local.x/content.width,local.y/content.height))?.let(selected)
            }
        })}) {
        val content=map.fittedSize(size)
        withTransform({translate(viewport.offset.x,viewport.offset.y);scale(viewport.scale,viewport.scale,pivot=Offset.Zero)}) {
            drawImage(bitmap,srcOffset=IntOffset.Zero,srcSize=IntSize(bitmap.width,bitmap.height),dstOffset=IntOffset.Zero,
                dstSize=IntSize(content.width.toInt(),content.height.toInt()),filterQuality=FilterQuality.None)
            target?.let {
                val p=map.mmToCanvas(it.first,it.second,content.width,content.height)
                drawCircle(Color.White,10.dp.toPx()/viewport.scale,p,style=Stroke(3.dp.toPx()/viewport.scale))
                drawCircle(Color(0xff3185ff),5.dp.toPx()/viewport.scale,p)
            }
        }
    }
}

