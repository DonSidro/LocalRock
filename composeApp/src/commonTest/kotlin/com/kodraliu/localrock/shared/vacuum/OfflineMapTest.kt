package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineMapTest {
    private fun read(json: String) = parseOfflineMap(Json.parseToJsonElement(json))

    // The shared register a robot was seen to answer: 25 with Offline Map on, 24 with it off.
    @Test
    fun bit_zero_of_the_shared_register_is_offline_map() {
        assertEquals(true, read("""{"common_switch_status":25}"""))
        assertEquals(false, read("""{"common_switch_status":24}"""))
    }

    @Test
    fun the_answer_may_come_in_a_list() {
        assertEquals(true, read("""[{"common_switch_status":1}]"""))
    }

    @Test
    fun any_other_answer_is_unknown() {
        assertNull(read("[]"))
        assertNull(read("null"))
        assertNull(read("""{"other":1}"""))
    }
}
