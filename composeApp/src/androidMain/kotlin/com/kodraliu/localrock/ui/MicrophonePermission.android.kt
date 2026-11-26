package com.kodraliu.localrock.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.kodraliu.localrock.shared.webrtc.MicrophoneAccess
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

@Composable
actual fun MicrophonePermissionHost() {
    val pending = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.getAndSet(null)?.complete(granted)
    }
    DisposableEffect(launcher) {
        MicrophoneAccess.requester = {
            val answer = CompletableDeferred<Boolean>()
            pending.getAndSet(answer)?.complete(false)
            launcher.launch(Manifest.permission.RECORD_AUDIO)
            answer.await()
        }
        onDispose {
            MicrophoneAccess.requester = null
            pending.getAndSet(null)?.complete(false)
        }
    }
}
