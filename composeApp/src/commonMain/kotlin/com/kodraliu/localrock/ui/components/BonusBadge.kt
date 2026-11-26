package com.kodraliu.localrock.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** LocalRock for server-side extras the official app lacks, Beta for behaviour not yet verified on a robot. */
enum class BonusKind(val label: String) {
    LocalRock("LocalRock"),
    Beta("Beta"),
}

/** Small chip marking a bonus row inside an official section. */
@Composable
fun BonusBadge(kind: BonusKind, modifier: Modifier = Modifier) {
    val beta = kind == BonusKind.Beta
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (beta) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (beta) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(kind.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
