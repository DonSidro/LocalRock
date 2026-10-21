package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SmartWashSettingsTest {
    @Test
    fun room_and_smart_replies_keep_their_mode_and_interval() {
        for ((mode, label) in listOf(1 to "After each room", 2 to "Smart")) {
            val result = Json.parseToJsonElement("""{"smart_wash":$mode,"wash_interval":1200}""")
            val settings = parseSmartWashSettings(result)
            assertEquals(SmartWashSettings(mode, 1200), settings)
            assertEquals(label, washFrequencyLabel(settings))
            assertEquals(result, encodeSmartWashParams(settings!!))
        }
    }

    @Test
    fun only_explicit_time_selection_changes_the_interval() {
        val original = SmartWashSettings(0, 1200)
        val byRoom = original.withFrequency(1, null)
        val smart = byRoom.withFrequency(2, null)
        assertEquals(SmartWashSettings(1, 1200), byRoom)
        assertEquals(SmartWashSettings(2, 1200), smart)
        assertEquals(
            Json.parseToJsonElement("""{"smart_wash":0,"wash_interval":900}"""),
            encodeSmartWashParams(smart.withFrequency(0, 15)),
        )
        assertEquals("15 min", washFrequencyLabel(smart.withFrequency(0, 15)))
        assertFailsWith<IllegalArgumentException> { smart.withFrequency(0, null) }
    }

    @Test
    fun missing_or_unknown_settings_are_not_assumed_to_be_time_based() {
        assertNull(parseSmartWashSettings(Json.parseToJsonElement("""{"wash_interval":1200}""")))
        assertNull(parseSmartWashSettings(Json.parseToJsonElement("""{"smart_wash":0}""")))
        assertNull(DockSettings().smartWash)
        assertEquals("Unavailable", washFrequencyLabel(null))
        assertEquals("Unknown (9)", washFrequencyLabel(SmartWashSettings(9, 1200)))
        assertEquals("1250 sec", washFrequencyLabel(SmartWashSettings(0, 1250)))
    }
}
