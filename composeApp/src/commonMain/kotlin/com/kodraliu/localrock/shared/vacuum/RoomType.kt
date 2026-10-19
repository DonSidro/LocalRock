package com.kodraliu.localrock.shared.vacuum

/**
 * The room types the official app offers when you edit a room. The robot keeps the type as a
 * numeric tag per room, and uses it to adjust cleaning preferences and order in the smart modes.
 * The cloud room carries the same label for display. Living room, Dining room, Kitchen and Hall
 * were read back from the robot after the official app set them; the rest come from the official
 * app's own tag table.
 */
enum class RoomType(val label: String, val tag: Int) {
    LIVING_ROOM("Living room", 6),
    KITCHEN("Kitchen", 14),
    DINING_ROOM("Dining room", 13),
    BEDROOM("Bedroom", 1),
    MASTER_BEDROOM("Master bedroom", 2),
    GUEST_BEDROOM("Guest bedroom", 3),
    BATHROOM("Bathroom", 15),
    STUDY("Study", 9),
    CORRIDOR("Corridor", 8),
    HALL("Hall", 10),
    BALCONY("Balcony", 7);

    companion object {
        fun fromTag(tag: Int): RoomType? = entries.firstOrNull { it.tag == tag }
    }
}
