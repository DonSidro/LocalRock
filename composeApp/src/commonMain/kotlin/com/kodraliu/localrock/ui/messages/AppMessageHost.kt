package com.kodraliu.localrock.ui.messages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.messages.AppMessage
import com.kodraliu.localrock.shared.messages.MessageCenter
import com.kodraliu.localrock.shared.messages.MessageSeverity
import kotlinx.coroutines.delay

private data class SeverityVisuals(val container: Color, val content: Color, val icon: ImageVector)

@Composable
private fun visualsFor(severity: MessageSeverity): SeverityVisuals {
    val scheme = MaterialTheme.colorScheme
    return when (severity) {
        MessageSeverity.ERROR -> SeverityVisuals(scheme.errorContainer, scheme.onErrorContainer, Icons.Default.Error)
        MessageSeverity.WARNING -> SeverityVisuals(Color(0xFFFFE0B2), Color(0xFF5C3A00), Icons.Default.Warning)
        MessageSeverity.SUCCESS -> SeverityVisuals(Color(0xFFC8E6C9), Color(0xFF1B5E20), Icons.Default.CheckCircle)
        MessageSeverity.INFO -> SeverityVisuals(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Default.Info)
    }
}


@Composable
fun MessageBannerArea(center: MessageCenter, modifier: Modifier = Modifier) {
    val banners by center.banners.collectAsState()
    Column(modifier.fillMaxWidth()) {
        banners.forEach { banner ->
            val v = visualsFor(banner.severity)
            Surface(color = v.container, contentColor = v.content, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(v.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(banner.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (banner.dismissible) {
                        IconButton(onClick = { center.dismissBanner(banner.key) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BoxScope.TransientMessageHost(center: MessageCenter) {
    var current by remember { mutableStateOf<AppMessage?>(null) }
    LaunchedEffect(center) {
        center.transient.collect { msg ->
            current = msg
            delay(if (msg.severity == MessageSeverity.ERROR) 5000 else 3000)
            if (current?.id == msg.id) current = null
        }
    }
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        current?.let { msg ->
            val v = visualsFor(msg.severity)
            Surface(
                color = v.container,
                contentColor = v.content,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(v.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(msg.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { current = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
