package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom

internal data class RoomLabelRequest(
    val id: Int, val anchor: Offset, val size: Size,
    val selectedOrder: Int = -1, val active: Boolean = false,
)
internal data class RoomLabelPlacement(val id: Int, val anchor: Offset, val bounds: Rect)

/** Place selected labels first in cleaning order. Crowding must not silently remove a selection. */
internal fun placeRoomLabels(requests: List<RoomLabelRequest>, viewport: Size, gap: Float): List<RoomLabelPlacement> {
    if (viewport.width <= 0f || viewport.height <= 0f) return emptyList()
    val placed = mutableListOf<RoomLabelPlacement>()
    val ordered = requests.sortedWith(compareBy<RoomLabelRequest>(
        { if (it.selectedOrder >= 0) 0 else if (it.active) 1 else 2 },
        { if (it.selectedOrder >= 0) it.selectedOrder else it.id },
    ))
    for (request in ordered) {
        val anchor = request.anchor
        if (anchor.x !in 0f..viewport.width || anchor.y !in 0f..viewport.height) continue
        val width = request.size.width.coerceAtMost(viewport.width)
        val height = request.size.height.coerceAtMost(viewport.height)
        fun rectAt(x: Float, y: Float): Rect {
            val left = (x - width / 2).coerceIn(0f, viewport.width - width)
            val top = (y - height / 2).coerceIn(0f, viewport.height - height)
            return Rect(left, top, left + width, top + height)
        }
        // Candidate positions immediately beside existing labels avoid jumping to distant rooms.
        val xs = listOf(anchor.x, width / 2, viewport.width - width / 2) + placed.flatMap {
            listOf(it.bounds.left - gap - width / 2, it.bounds.right + gap + width / 2)
        }
        val ys = listOf(anchor.y, height / 2, viewport.height - height / 2) + placed.flatMap {
            listOf(it.bounds.top - gap - height / 2, it.bounds.bottom + gap + height / 2)
        }
        val rect = xs.flatMap { x -> ys.map { y -> rectAt(x, y) } }
            .distinct()
            .filter { r -> placed.none { it.bounds.inflate(gap / 2).overlaps(r.inflate(gap / 2)) } }
            .minByOrNull { (it.center - anchor).getDistanceSquared() }
            // If the screen is completely full, retain selected/active labels rather than cull them.
            ?: if (request.selectedOrder >= 0 || request.active) rectAt(anchor.x, anchor.y) else continue
        placed += RoomLabelPlacement(request.id, anchor, rect)
    }
    return placed
}

internal fun List<RoomLabelPlacement>.roomAtLabel(point: Offset): Int? =
    asReversed().firstOrNull { it.bounds.contains(point) }?.id

internal data class MapRoomLabel(
    val placement: RoomLabelPlacement, val text: TextLayoutResult, val icon: Painter?,
    val iconSize: Float, val iconSpace: Float, val background: Color, val foreground: Color,
)

@Composable
internal fun mapRoomLabels(
    rooms: List<ParsedMapRoom>, selectedIds: List<Int>, activeIds: Set<Int>, showIcons: Boolean,
    content: Size, viewport: Size, offset: Offset, scale: Float,
): List<MapRoomLabel> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val labels = rooms.mapNotNull { room ->
        val x = room.labelNormX ?: return@mapNotNull null
        val y = room.labelNormY ?: return@mapNotNull null
        val order = selectedIds.indexOf(room.id)
        val foreground = if (order >= 0) scheme.onPrimary else scheme.onSurface
        val text = measurer.measure(
            if (order >= 0) "${order + 1} · ${room.name}" else room.name,
            TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = foreground),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = with(density) { 116.dp.roundToPx() }),
        )
        val icon = if (showIcons) mapRoomIcon(room.roomTypeTag)?.let { rememberVectorPainter(it) } else null
        val iconSize = if (icon != null) with(density) { 14.dp.toPx() } else 0f
        val iconSpace = if (icon != null) iconSize + with(density) { 4.dp.toPx() } else 0f
        val size = Size(text.size.width + iconSpace + with(density) { 10.dp.toPx() },
            maxOf(text.size.height.toFloat(), iconSize) + with(density) { 6.dp.toPx() })
        val anchor = offset + Offset(x * content.width, y * content.height) * scale
        RoomLabelRequest(room.id, anchor, size, order, room.id in activeIds) to MapRoomLabel(
            RoomLabelPlacement(room.id, anchor, Rect(Offset.Zero, size)), text, icon, iconSize, iconSpace,
            if (order >= 0) scheme.primary else scheme.surface.copy(alpha = .9f), foreground,
        )
    }
    val byId = labels.associate { it.first.id to it.second }
    return placeRoomLabels(labels.map { it.first }, viewport, with(density) { 3.dp.toPx() })
        .map { byId.getValue(it.id).copy(placement = it) }
}

internal fun DrawScope.drawRoomLabels(labels: List<MapRoomLabel>) {
    // Leaders connect moved labels to their room, underneath every label's background.
    for (label in labels) {
        val anchor = label.placement.anchor
        val rect = label.placement.bounds
        if (!rect.contains(anchor)) drawLine(label.foreground.copy(alpha = .65f), anchor, rect.center, 1.dp.toPx())
    }
    for (label in labels) {
        val rect = label.placement.bounds
        drawRoundRect(label.background, rect.topLeft, rect.size, CornerRadius(4.dp.toPx()))
        val x = rect.left + 5.dp.toPx()
        label.icon?.let { painter ->
            withTransform({ translate(x, rect.center.y - label.iconSize / 2) }) {
                with(painter) { draw(Size(label.iconSize, label.iconSize), colorFilter = ColorFilter.tint(label.foreground)) }
            }
        }
        drawText(label.text, topLeft = Offset(x + label.iconSpace, rect.center.y - label.text.size.height / 2f))
    }
}
