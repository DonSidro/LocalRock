package com.kodraliu.localrock.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Radio list of [options], each with its line from [details] when given; the caller closes the dialog in [onSelect]. */
@Composable
fun PickerDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    details: List<String?> = emptyList(),
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    // The whole row selects, not just the radio button.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = index == selected, enabled = enabled, role = Role.RadioButton) { onSelect(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, enabled = enabled, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        val detail = details.getOrNull(index)
                        if (detail == null) {
                            Text(option, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Text(option, style = MaterialTheme.typography.bodyMedium)
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
