package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.MatterInfo
import com.kodraliu.localrock.shared.vacuum.MatterState
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_POLLS = 45
private const val POLL_MS = 2_000L

/** The 11-digit manual code is shown as 4-3-4, the way Matter apps print it. */
internal fun groupedManualCode(code: String): String =
    if (code.length == 11 && code.all { it.isDigit() }) "${code.take(4)}-${code.substring(4, 7)}-${code.takeLast(4)}" else code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatterScreen(
    loadStatus: suspend () -> MatterInfo?,
    requestKey: suspend () -> Unit,
    reset: suspend () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var info by remember { mutableStateOf<MatterInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var confirmLeave by remember { mutableStateOf(false) }

    // A first look reports a failed key as it is; a retry asks the robot for the key again.
    LaunchedEffect(attempt) {
        error = null
        var asked = false
        repeat(MAX_POLLS) {
            val read = runCatching { loadStatus() }
            if (read.isFailure) {
                error = read.exceptionOrNull()?.message ?: "The robot did not answer"
                return@LaunchedEffect
            }
            val current = read.getOrNull()
            info = current
            val state = current?.state
            if (state == null) {
                error = "The robot gave no Matter status"
                return@LaunchedEffect
            }
            if (state.hasCodes) return@LaunchedEffect
            val needsKey = state == MatterState.KeyNotExit ||
                (attempt > 0 && state.failed)
            if (needsKey && !asked) {
                asked = true
                val sent = runCatching { requestKey() }
                if (sent.isFailure) {
                    error = sent.exceptionOrNull()?.message ?: "The robot refused the key request"
                    return@LaunchedEffect
                }
            } else if (state.failed) {
                return@LaunchedEffect
            }
            delay(POLL_MS)
        }
        error = "The robot is still preparing its setup code. Try again in a moment."
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Matter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        CenteredContent {
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Add the robot to Apple Home, Google Home, Alexa, SmartThings, Home Assistant or any app that supports Matter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = info
                val state = current?.state
                when {
                    error != null -> MatterNotice(error!!, "Try again") { attempt++ }
                    state != null && state.hasCodes -> {
                        if (state == MatterState.Joined) {
                            Text(
                                "This robot has joined a Matter platform. To add it to another one, share it from the app it joined, or use this code.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        current.manualCode?.let { code ->
                            MatterCodeCard("Setup code", groupedManualCode(code)) { clipboard.setText(AnnotatedString(code)) }
                        }
                        current.qrPayload?.let { qr ->
                            MatterCodeCard("QR code text", qr) { clipboard.setText(AnnotatedString(qr)) }
                        }
                        Text(
                            "The code is valid for 15 minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state == MatterState.Joined) {
                            OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Leave the Matter platform")
                            }
                        }
                    }
                    state != null && state.failed ->
                        MatterNotice("The robot could not get its Matter key. Move it where the Wi-Fi is stronger and try again.", "Try again") { attempt++ }
                    else -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Getting the setup code…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the Matter platform?") },
            text = { Text("The robot is removed from the platform it joined. You can add it again afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch {
                        runCatching { reset() }.onFailure { error = it.message ?: "The robot refused to reset Matter" }
                        if (error == null) attempt++
                    }
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MatterNotice(message: String, action: String, onAction: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun MatterCodeCard(label: String, value: String, onCopy: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                }
            }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
        }
    }
}
