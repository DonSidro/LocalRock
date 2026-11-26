package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom

/**
 * The order a full clean goes through the rooms in. Starts from the saved order, with rooms it
 * does not name added at the end; arrows move a room up or down.
 */
@Composable
internal fun CleanOrderSheetContent(
    rooms: List<ParsedMapRoom>,
    savedOrder: List<Int>,
    busy: Boolean,
    onSave: (List<Int>) -> Unit,
    onRemove: () -> Unit,
) {
    val initial = remember(rooms, savedOrder) {
        val onMap = rooms.map { it.id }
        (savedOrder.filter { it in onMap } + onMap).distinct()
    }
    var order by remember(initial) { mutableStateOf(initial) }
    val names = remember(rooms) { rooms.associate { it.id to it.name } }
    fun move(from: Int, to: Int) {
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Default cleaning order", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (rooms.isEmpty()) "Load the map first to discover your rooms."
            else "Clean without picking rooms goes through them in this order. Rooms you pick are still cleaned in the order you tap them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            order.forEachIndexed { index, roomId ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        names[roomId] ?: "Room $roomId",
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { move(index, index - 1) }, enabled = !busy && index > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${names[roomId] ?: "room"} up")
                    }
                    IconButton(onClick = { move(index, index + 1) }, enabled = !busy && index < order.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${names[roomId] ?: "room"} down")
                    }
                }
            }
        }
        if (rooms.isNotEmpty()) {
            Button(
                onClick = { onSave(order) },
                enabled = !busy && order != savedOrder,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text("Save order")
            }
        }
        if (savedOrder.isNotEmpty()) {
            TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Remove default order")
            }
        }
    }
}
