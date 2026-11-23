package com.kodraliu.localrock.shared.vacuum
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import kotlin.test.*
class ActiveRoomDisplayTest {
    @Test fun b01_falls_back_to_current_segment_session_only() {
        assertEquals(setOf(2), activeRoomDisplayIds(null, MapFormat.B01, 3, setOf(2)))
        assertEquals(emptySet(), activeRoomDisplayIds(null, MapFormat.B01, null, setOf(2)))
        assertEquals(emptySet(), activeRoomDisplayIds(null, MapFormat.B01, 2, setOf(2)))
        assertEquals(emptySet(), activeRoomDisplayIds(null, MapFormat.LEGACY, 3, setOf(2)))
        assertEquals(setOf(4), activeRoomDisplayIds(setOf(4), MapFormat.B01, 3, setOf(2)))
    }
}
