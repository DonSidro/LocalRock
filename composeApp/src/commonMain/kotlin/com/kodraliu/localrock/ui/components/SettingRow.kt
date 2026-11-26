package com.kodraliu.localrock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/** Padding of one settings row inside a list. */
val SettingRowPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)

/** The tinted rounded square that leads every settings row. */
@Composable
fun SettingIconTile(icon: ImageVector, tint: Color) {
    val iconColor = legibleIconTint(tint, MaterialTheme.colorScheme.surfaceContainer)
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = TILE_ALPHA), modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp), iconColor) }
    }
}

/** Dark amber for yellow icons; passes 3:1 against its own tile in light and dark mode. */
val SettingAmber = Color(0xFFB26A00)

private const val TILE_ALPHA = 0.12f

/** WCAG relative luminance of an sRGB color. */
internal fun relativeLuminance(c: Color): Double {
    fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

internal fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun mix(a: Color, b: Color, f: Float) = Color(
    red = a.red + (b.red - a.red) * f, green = a.green + (b.green - a.green) * f, blue = a.blue + (b.blue - a.blue) * f,
)

/** [tint], darkened on a light group or lightened on a dark one until it reaches 3:1 (WCAG graphical object) against its tile. */
internal fun legibleIconTint(tint: Color, groupBackground: Color, minRatio: Double = 3.0): Color {
    val tile = mix(groupBackground, tint, TILE_ALPHA)
    val toward = if (relativeLuminance(groupBackground) > 0.18) Color.Black else Color.White
    var step = 0
    var icon = tint
    while (contrastRatio(icon, tile) < minRatio && step < 20) {
        step++
        icon = mix(tint, toward, step * 0.05f)
    }
    return icon
}

/** A row that opens a page: icon, title, subtitle and a chevron. */
@Composable
fun SettingsCategoryRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SettingRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingIconTile(icon, iconTint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A row that opens a picker or runs an action; dimmed when disabled. [value] sits at the end, before the chevron. */
@Composable
fun SettingRowClickable(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    value: String? = null,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).padding(SettingRowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingIconTile(icon, iconTint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    // Narrow enough that the description keeps most of the row on a phone.
                    modifier = Modifier.widthIn(max = 128.dp),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

/** A read-only row: title over a value, with an optional supporting line. */
@Composable
fun SettingRowInfo(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    valueSupporting: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(SettingRowPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingIconTile(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (valueSupporting != null) {
                Text(valueSupporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A switch row. Without [icon] it has no leading tile; [padding] lets a row sit inside an already padded block. */
@Composable
fun SettingRowSwitch(
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    padding: PaddingValues = SettingRowPadding,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) SettingIconTile(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}
