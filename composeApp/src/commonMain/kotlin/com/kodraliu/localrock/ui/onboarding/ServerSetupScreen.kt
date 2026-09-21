package com.kodraliu.localrock.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.LocalAppContainer

private const val SERVER_GUIDE_URL = "https://github.com/Python-roborock/local_roborock_server"

/**
 * First run used to drop people into the full Settings screen — theme picker, licences and all —
 * just because no server URL was stored. This is the one thing that actually needs answering,
 * asked on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val container = LocalAppContainer.current
    val uriHandler = LocalUriHandler.current
    val savedUrl by container.appSettings.serverBaseUrl.collectAsState()
    val userData by container.authRepository.userData.collectAsState()

    var url by remember { mutableStateOf(savedUrl ?: "") }
    val trimmed = url.trim()
    val looksValid = trimmed.startsWith("http://") || trimmed.startsWith("https://")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (savedUrl.isNullOrBlank()) "Connect" else "Server") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                "Where is your server?",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "LocalRock talks only to the LocalRock server running on your own network. " +
                    "Enter its address to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address") },
                placeholder = { Text("https://192.168.1.10:555") },
                singleLine = true,
                isError = url.isNotBlank() && !looksValid,
                supportingText = {
                    Text(
                        if (url.isNotBlank() && !looksValid) {
                            "Start with http:// or https://"
                        } else {
                            // The Ktor client keeps only scheme/host/port, so a trailing path is
                            // silently dropped — worth saying before someone debugs it for an hour.
                            "Use the server root, including the port. Don't add /admin."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = looksValid,
                onClick = {
                    container.appSettings.setServerBaseUrl(trimmed)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Continue") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { uriHandler.openUri(SERVER_GUIDE_URL) }) {
                    Text("Server setup guide")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }

            if (userData == null) {
                Text(
                    "No server yet?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { container.authRepository.enterDemo() }) {
                    Text("Explore the demo instead")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
