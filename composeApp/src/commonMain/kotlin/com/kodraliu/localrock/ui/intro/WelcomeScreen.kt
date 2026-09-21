package com.kodraliu.localrock.ui.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.LocalAppContainer

private const val PROJECT_URL = "https://github.com/Python-roborock/local_roborock_server"


@Composable
fun WelcomeScreen() {
    val container = LocalAppContainer.current
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Control your vacuum\nwithout the cloud",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "LocalRock runs entirely on your own network. Three things to know before you start.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Point(
                Icons.Default.Dns,
                "You need the server first",
                "LocalRock talks to a self-hosted LocalRock server. Until that is running on your " +
                    "network, the app has nothing to control.",
            )
            Point(
                Icons.Default.VpnKey,
                "Sign in with server credentials",
                "The email and login code come from your own server setup — not a new Roborock account.",
            )
            Point(
                Icons.Default.Wifi,
                "Stay on the same network",
                "Your phone and the server have to be able to reach each other locally.",
            )

            TextButton(
                onClick = { uriHandler.openUri(PROJECT_URL) },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("Server setup guide")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            Text(
                "LocalRock is an independent community project, not affiliated with or endorsed by Roborock.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        // The way forward stays put at the bottom instead of drifting with the scroll.
        Button(
            onClick = { container.appSettings.setIntroSeen(true) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .height(52.dp),
        ) {
            Text("Get started")
        }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
