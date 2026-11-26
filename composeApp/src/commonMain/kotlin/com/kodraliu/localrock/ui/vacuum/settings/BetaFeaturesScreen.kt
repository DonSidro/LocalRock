package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.AdvertisedFeature
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.ui.AppColors
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.components.SettingRowSwitch
import androidx.compose.runtime.collectAsState
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection

private const val BETA_FEATURES_ISSUE_URL = "https://github.com/DonSidro/LocalRock/issues/55"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BetaFeaturesScreen(
    features: List<BetaFeatureState>,
    loading: Boolean,
    unlisted: List<AdvertisedFeature>,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showUnlisted by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beta features") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        CenteredContent {
            LazyColumn(contentPadding = padding) {
                item {
                    Text(
                        "Options your robot reports as supported. Once on, the robot uses them during its cleaning cycle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (loading) {
                    item {
                        Text(
                            "Checking your robot…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else if (features.isEmpty() && unlisted.isEmpty()) {
                    item {
                        Text(
                            "Your robot doesn't report any beta features.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(features, key = { it.def.id }) { f ->
                    Column {
                        SettingRowSwitch(
                            icon = Icons.Default.Science,
                            iconTint = AppColors.AccentCyan,
                            title = f.def.title,
                            subtitle = f.def.description,
                            checked = f.enabled,
                            onCheckedChange = { onToggle(f.def.id, it) },
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
                if (unlisted.isNotEmpty()) {
                    item {
                        Surface(onClick = { showUnlisted = !showUnlisted }, color = Color.Transparent) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Also advertised by your robot", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${unlisted.size} features with no controls yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(
                                    if (showUnlisted) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (showUnlisted) "Hide" else "Show",
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                    if (showUnlisted) {
                        item {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(
                                    "These are the feature bits your robot reports that LocalRock has no switch for. " +
                                        "Tell us which you want, and your robot model, on GitHub.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { uriHandler.openUri(BETA_FEATURES_ISSUE_URL) }) {
                                    Text("Report on GitHub")
                                }
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("Feature", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text("Bit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        items(unlisted, key = { it.bit }) { f ->
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Text(
                                        f.name ?: "Unnamed",
                                        Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (f.name == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                                    )
                                    Text(f.bit.toString(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VacuumBetaFeaturesScreen(viewModel: VacuumViewModel, onBack: () -> Unit) {
    val betaFeatures by viewModel.repository.betaFeatures.collectAsState()
    val betaFeaturesLoading by viewModel.repository.betaFeaturesLoading.collectAsState()
    BetaFeaturesScreen(
        features = betaFeatures.inSection(FeatureSection.BETA),
        loading = betaFeaturesLoading,
        unlisted = viewModel.unlistedFeatures.collectAsState().value,
        onToggle = { id, on -> viewModel.report { it.setBetaFeature(id, on) } },
        onBack = onBack,
    )
}
