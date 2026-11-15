package com.kodraliu.localrock.ui.vacuum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter

/** The glyph shown in front of a room's name, chosen by the room type the robot keeps as a tag. */
internal class RoomIconPainters(private val byTag: Map<Int, Painter>, private val unnamed: Painter) {
    fun forTag(tag: Int?): Painter = tag?.let { byTag[it] } ?: unnamed
}

@Composable
internal fun rememberRoomIconPainters(): RoomIconPainters {
    val bed = rememberVectorPainter(Icons.Default.Bed)
    val sofa = rememberVectorPainter(Icons.Default.Weekend)
    val dining = rememberVectorPainter(Icons.Default.Restaurant)
    val kitchen = rememberVectorPainter(Icons.Default.Kitchen)
    val bath = rememberVectorPainter(Icons.Default.Bathtub)
    val book = rememberVectorPainter(Icons.Default.Book)
    val door = rememberVectorPainter(Icons.Default.MeetingRoom)
    val deck = rememberVectorPainter(Icons.Default.Deck)
    val home = rememberVectorPainter(Icons.Default.Home)
    return remember(bed, sofa, dining, kitchen, bath, book, door, deck, home) {
        // Tags as the robot numbers them (see RoomType): bedrooms 1 to 3, balcony 7, corridor 8, study 9,
        // hall 10, living room 6, dining room 13, kitchen 14, bathroom 15.
        RoomIconPainters(
            mapOf(
                1 to bed, 2 to bed, 3 to bed, 6 to sofa, 7 to deck, 8 to door, 9 to book,
                10 to door, 13 to dining, 14 to kitchen, 15 to bath,
            ),
            home,
        )
    }
}
