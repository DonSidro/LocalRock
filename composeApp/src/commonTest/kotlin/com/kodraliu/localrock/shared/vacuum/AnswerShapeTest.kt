package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// The answer shapes a Qrevo Pro (a101, firmware 02.15.26) was seen to use for the same kind of read.
class AnswerShapeTest {

    private fun int(json: String, key: String? = null) = Json.parseToJsonElement(json).asAnswerInt(key)

    @Test
    fun a_number_is_read_whether_bare_or_in_a_list() {
        assertEquals(45, int("45"))
        assertEquals(45, int("[45]"))
    }

    @Test
    fun a_field_is_read_whether_in_an_object_or_a_list_of_one() {
        assertEquals(0, int("""{"lock_status":0}""", "lock_status"))
        assertEquals(1, int("""[{"carpet_clean_mode":1}]""", "carpet_clean_mode"))
    }

    @Test
    fun anything_else_is_null_and_never_throws() {
        assertNull(int("[]"))
        assertNull(int("null"))
        assertNull(int("""{"other":1}""", "lock_status"))
        assertNull(int("""{"lock_status":1}"""))
        assertNull(int("\"ok\""))
    }
}
