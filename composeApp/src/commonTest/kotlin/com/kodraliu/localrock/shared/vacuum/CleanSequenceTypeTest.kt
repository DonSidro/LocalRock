package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CleanSequenceTypeTest {

    @Test
    fun sequence_params_are_a_bare_object_with_repeat_only_for_supported_vacuum_then_mop() {
        assertEquals(
            """{"type":1,"fan_power":103,"water_box_mode":203,"mop_mode":300,"repeat":1}""",
            cleanSequenceTypeParams(1, 103, 203, 300, repeat = 1).toString(),
        )
        assertEquals(
            """{"type":1,"fan_power":103,"water_box_mode":203,"mop_mode":300}""",
            cleanSequenceTypeParams(1, 103, 203, 300).toString(),
        )
        assertEquals(
            """{"type":0,"fan_power":103,"water_box_mode":203,"mop_mode":300}""",
            cleanSequenceTypeParams(0, 103, 203, 300, repeat = 1).toString(),
        )
    }

    @Test
    fun partial_status_preserves_sequence_type_until_an_explicit_change() {
        val status = Json.decodeFromString<VacuumStatus>("""{"seq_type":1}""")
        assertEquals(1, status.cleanSequenceType)

        val partial = status.merge(Json.decodeFromString<VacuumStatus>("""{"fan_power":104}"""))
        assertEquals(1, partial.cleanSequenceType)

        val cleared = partial.merge(Json.decodeFromString<VacuumStatus>("""{"seq_type":0}"""))
        assertEquals(0, cleared.cleanSequenceType)
    }
}
