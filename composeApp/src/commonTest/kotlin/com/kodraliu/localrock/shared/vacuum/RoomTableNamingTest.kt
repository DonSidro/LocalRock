package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomTableNamingTest {
    private val room = ParsedMapRoom(id = 16, name = "Room 16")
    private val homeNames = mapOf(7600617L to "Kitchen")

    @Test
    fun a_row_table_without_tags_still_names_the_room() {
        val rows = Json.parseToJsonElement("""[[16,"7600617"],[20,"8627864"]]""")
        val table = parseSegmentTable(rows)
        val resolved = room.withRoomTable(parseRoomMapping(rows), homeNames, table)
        assertEquals("Kitchen", resolved.name)
        assertNull(resolved.tag)
        assertTrue(resolved.inRoomTable)
    }

    @Test
    fun a_cleared_raw_table_keeps_the_room_named() {
        val mapping = parseRoomMapping(Json.parseToJsonElement("""[[16,"7600617"]]"""))
        val resolved = room.withRoomTable(mapping, homeNames, table = null)
        assertEquals("Kitchen", resolved.name)
        assertTrue(resolved.inRoomTable)
    }

    @Test
    fun a_tagged_row_carries_its_tag() {
        val rows = Json.parseToJsonElement("""[[16,"7600617",14]]""")
        val resolved = room.withRoomTable(parseRoomMapping(rows), homeNames, parseSegmentTable(rows))
        assertEquals(14, resolved.tag)
        assertTrue(resolved.inRoomTable)
    }

    @Test
    fun a_room_missing_from_every_table_is_not_in_the_room_table() {
        val rows = Json.parseToJsonElement("""[[20,"8627864"]]""")
        val resolved = room.withRoomTable(parseRoomMapping(rows), homeNames, parseSegmentTable(rows))
        assertEquals("Room 16", resolved.name)
        assertFalse(resolved.inRoomTable)
        assertFalse(room.withRoomTable(emptyMap(), emptyMap(), null).inRoomTable)
    }
}
