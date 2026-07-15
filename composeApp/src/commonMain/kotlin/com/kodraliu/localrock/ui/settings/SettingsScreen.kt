package com.kodraliu.localrock.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.settings.ThemeMode
import com.kodraliu.localrock.ui.LocalAppContainer

const val APP_VERSION: String = "1.0.0"

private const val PROJECT_URL = "https://github.com/DonSidro/LocalRock/"

private const val PRIVACY_URL = "https://donsidro.github.io/LocalRock/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit, allowCancel: Boolean) {
    val container = LocalAppContainer.current
    val uriHandler = LocalUriHandler.current
    val savedUrl by container.appSettings.serverBaseUrl.collectAsState()
    val themeMode by container.appSettings.themeMode.collectAsState()
    val userData by container.authRepository.userData.collectAsState()

    var url by remember { mutableStateOf(savedUrl ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (allowCancel) {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel("Appearance")
            val themeModes = listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                themeModes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { container.appSettings.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                    ) {
                        Text(label)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SectionLabel("Connection")
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://api-test.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "The address of your self-hosted LocalRock server on your network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Button(
                enabled = url.isNotBlank(),
                onClick = {
                    container.appSettings.setServerBaseUrl(url.trim())
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (allowCancel) {
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
            if (userData == null) {
                TextButton(
                    onClick = { container.authRepository.enterDemo() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Explore the demo (no server needed)") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SectionLabel("Contribute")
            Text(
                "LocalRock is a free, open-source community project. Issue reports, ideas, and " +
                    "pull requests are all welcome.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { uriHandler.openUri(PROJECT_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Contribute on GitHub")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SectionLabel("About")
            Text(
                "LocalRock $APP_VERSION",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Control vacuums locally through your own server. Not affiliated with, " +
                    "endorsed by, or connected to Roborock. \"Roborock\" is a trademark of its owner.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { uriHandler.openUri(PRIVACY_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Privacy policy") }

            Spacer(Modifier.height(4.dp))
            Text(
                "Thanks & open-source licenses",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                ACKNOWLEDGEMENTS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Made with ❤️ for the self-hosting community.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

private const val ACKNOWLEDGEMENTS =
    "LocalRock stands on the shoulders of open-source work. With thanks to:\n\n" +
        "• Kotlin Multiplatform & Compose Multiplatform (Apache-2.0) — JetBrains\n" +
        "• Ktor client (Apache-2.0) — JetBrains\n" +
        "• kotlinx.serialization / coroutines (Apache-2.0) — JetBrains\n" +
        "• KMQTT (MIT) — Davide Pianca\n" +
        "• CocoaMQTT (MIT) — the emqx team\n" +
        "• stream-webrtc-android (Apache-2.0) — GetStream / the WebRTC project\n" +
        "• multiplatform-settings (Apache-2.0) — Russell Wolf\n" +
        "• KotlinCrypto hash & macs (Apache-2.0)\n" +
        "• Okio (Apache-2.0) — Square\n\n" +
        "And to the reverse-engineering community — especially the python-roborock " +
        "project — whose protocol work made local control possible."
