package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.camera.CameraLiveSession
import com.kodraliu.localrock.shared.webrtc.RtcVideoView
import com.kodraliu.localrock.shared.webrtc.liveViewSupported
import com.kodraliu.localrock.ui.LocalAppContainer


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraLiveViewScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val duid = viewModel.repository.duid
    val liveSession = remember { CameraLiveSession(viewModel.repository.session) }
    val state by liveSession.state.collectAsState()
    val peer by liveSession.peer.collectAsState()

    var pin by remember { mutableStateOf(container.appSettings.cameraPin(duid) ?: "") }
    var autoStarted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { liveSession.dispose() }
    }

    // If a PIN is already saved, connect immediately.
    LaunchedEffect(Unit) {
        if (liveViewSupported && pin.isNotBlank() && !autoStarted) {
            autoStarted = true
            liveSession.start(pin)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(viewModel.deviceName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (!liveViewSupported) {
                CameraMessage(
                    icon = { Icon(Icons.Default.VideocamOff, null, Modifier.size(48.dp), tint = Color.White) },
                    text = "Camera live view isn't available on this platform yet.",
                )
                return@Box
            }

            when (val s = state) {
                is CameraLiveSession.State.Idle -> {
                    PinEntry(
                        pin = pin,
                        onPinChange = { pin = it },
                        onConnect = {
                            container.appSettings.setCameraPin(duid, pin)
                            liveSession.start(pin)
                        },
                        onSetPinOnRobot = {
                            container.appSettings.setCameraPin(duid, pin)
                            liveSession.setPinAndStart(pin)
                        },
                    )
                }

                is CameraLiveSession.State.Connecting -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text(s.stage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is CameraLiveSession.State.Streaming -> {
                    peer?.let { RtcVideoView(it, Modifier.fillMaxSize()) }
                    Text(
                        "LIVE",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp, end = 16.dp)
                            .background(Color.Red.copy(alpha = 0.8f), MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                is CameraLiveSession.State.Failed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(Icons.Default.VideocamOff, null, Modifier.size(48.dp), tint = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            s.message,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { liveSession.start(pin) }) { Text("Retry") }
                        TextButton(onClick = {
                            container.appSettings.setCameraPin(duid, null)
                            pin = ""
                            liveSession.stop()
                        }) { Text("Change PIN", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinEntry(
    pin: String,
    onPinChange: (String) -> Unit,
    onConnect: () -> Unit,
    onSetPinOnRobot: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(
            "The live view is protected by a camera PIN stored on the robot. " +
                "Enter your existing PIN, or pick a new one and register it on the robot.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            label = { Text("Camera PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onConnect, enabled = pin.isNotBlank()) { Text("Start live view") }
        TextButton(onClick = onSetPinOnRobot, enabled = pin.isNotBlank()) {
            Text("Set as new PIN on robot", color = Color.White)
        }
    }
}

@Composable
private fun CameraMessage(icon: @Composable () -> Unit, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color.White, textAlign = TextAlign.Center)
    }
}
