package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material.icons.Icons
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

/** Use the reported room type; an unfamiliar tag gets no guessed icon. */
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
