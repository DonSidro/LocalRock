package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomTypeTest {
    @Test
    fun tags_match_what_the_robot_held_after_the_official_app_set_them() {
        assertEquals(6, RoomType.LIVING_ROOM.tag)
        assertEquals(14, RoomType.KITCHEN.tag)
        assertEquals(13, RoomType.DINING_ROOM.tag)
        assertEquals(10, RoomType.HALL.tag)
    }

    @Test
    fun every_tag_is_unique() {
        assertEquals(RoomType.entries.size, RoomType.entries.map { it.tag }.toSet().size)
    }

    @Test
    fun fromTag_finds_a_type_or_null() {
        assertEquals(RoomType.KITCHEN, RoomType.fromTag(14))
        assertNull(RoomType.fromTag(12))
    }
}
