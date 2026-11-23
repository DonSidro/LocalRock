package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material.icons.outlined.Balcony
import androidx.compose.material.icons.outlined.Bathtub
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Desk
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import com.kodraliu.localrock.shared.vacuum.RoomType


/** The glyph shown in front of a room's name, chosen by the room type the robot keeps as a tag. */

internal class RoomIconPainters(private val byTag: Map<Int, Painter>, private val unnamed: Painter) {
    fun forTag(tag: Int?): Painter = tag?.let { byTag[it] } ?: unnamed
}

@Composable
internal fun rememberRoomIconPainters(): RoomIconPainters {
    val byTag = RoomType.entries.mapNotNull { type ->
        mapRoomIcon(type.tag)?.let { type.tag to rememberVectorPainter(it) }
    }.toMap()
    val home = rememberVectorPainter(Icons.Default.Home)
    return remember(byTag, home) { RoomIconPainters(byTag, home) }
}

internal fun mapRoomIcon(tag: Int?): ImageVector? = when (tag?.let { RoomType.fromTag(it) }) {
    RoomType.LIVING_ROOM -> Icons.Outlined.Chair
    RoomType.KITCHEN -> Icons.Outlined.Kitchen
    RoomType.DINING_ROOM -> Icons.Outlined.Restaurant
    RoomType.BEDROOM, RoomType.MASTER_BEDROOM, RoomType.GUEST_BEDROOM -> Icons.Outlined.Bed
    RoomType.BATHROOM -> Icons.Outlined.Bathtub
    RoomType.STUDY -> Icons.Outlined.Desk
    RoomType.CORRIDOR -> Icons.Outlined.DoorFront
    RoomType.HALL -> Icons.Outlined.MeetingRoom
    RoomType.BALCONY -> Icons.Outlined.Balcony
    null -> null
}
