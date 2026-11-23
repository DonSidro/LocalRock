package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.ui.adaptive.CenteredContent
import com.kodraliu.localrock.ui.components.BonusBadge
import com.kodraliu.localrock.ui.components.BonusKind

/** A settings page: back arrow, title and a list of [SettingGroup] cards. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        CenteredContent {
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                content = content,
            )
        }
    }
}

/** The thin line between rows inside one card. */
@Composable
internal fun RowDivider() = HorizontalDivider(Modifier.padding(start = 76.dp))

/** A small caption above a card, for a titled official group or the LocalRock extras. */
@Composable
internal fun GroupCaption(text: String, badge: BonusKind? = null) {
    Row(
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (badge != null) BonusBadge(badge)
    }
}

/** A footnote under a card. */
@Composable
internal fun GroupFootnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
    )
}

/** Reads the robot's feature rows when a page is opened directly, not through the Settings root. */
@Composable
internal fun LoadFeatureRowsOnEntry(viewModel: VacuumViewModel) {
    val repository = viewModel.repository
    LaunchedOnEntry {
        if (repository.betaFeatures.value.isEmpty() && !repository.betaFeaturesLoading.value) {
            runCatching { repository.fetchBetaFeatures(viewModel.betaFeatureDefs.value) }
        }
    }
}
