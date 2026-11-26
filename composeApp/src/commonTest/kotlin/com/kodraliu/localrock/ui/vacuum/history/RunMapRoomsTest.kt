package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class RunMapRoomsTest {
    private val map = ParsedMap(
        width = 1, height = 1, grid = ByteArray(1), resolution = 0.05f,
        rooms = listOf(
            ParsedMapRoom(16, "Room 16", labelNormX = 0.2f, labelNormY = 0.3f),
            ParsedMapRoom(17, "Room 17"),
        ),
    )

    @Test
    fun copiesNameAndTypeBySegmentId() {
        val named = map.withRoomNames(listOf(ParsedMapRoom(16, "Kitchen", tag = 4)))
        val kitchen = named.rooms.first { it.id == 16 }
        assertEquals("Kitchen", kitchen.name)
        assertEquals(4, kitchen.tag)
        assertEquals(0.2f, kitchen.labelNormX)
    }

    @Test
    fun leavesUnknownSegmentsAlone() {
        val other = map.withRoomNames(listOf(ParsedMapRoom(16, "Kitchen", tag = 4))).rooms.first { it.id == 17 }
        assertEquals("Room 17", other.name)
        assertNull(other.tag)
    }

    @Test
    fun noKnownRoomsKeepsTheMap() {
        assertSame(map, map.withRoomNames(emptyList()))
    }
}
