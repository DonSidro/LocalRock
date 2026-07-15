package com.kodraliu.localrock.ui.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.LocalAppContainer

private const val PROJECT_URL = "https://github.com/Python-roborock/local_roborock_server"


@Composable
fun WelcomeScreen() {
    val container = LocalAppContainer.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Welcome to LocalRock",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Control your robot vacuum over your own network — please read this first.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Point(
            "1",
            "Set up the server first",
            "LocalRock talks to a self-hosted server that you run on your network. " +
                "The app does nothing until that server is up. If you haven't set it up yet, do that before continuing.",
        )
        Point(
            "2",
            "Sign in with your server credentials",
            "You'll sign in with the email and login code you configured during server setup — " +
                "not a new cloud account.",
        )
        Point(
            "3",
            "Stay on the same network",
            "Your phone and the server need to be reachable on the same local network for control to work.",
        )
        Point(
            "4",
            "Independent project",
            "LocalRock is a community app and is not affiliated with or endorsed by Roborock.",
        )

        TextButton(onClick = { uriHandler.openUri(PROJECT_URL) }) {
            Text("Server setup guide")
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { container.appSettings.setIntroSeen(true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("I understand — get started")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Point(number: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
