package com.kodraliu.localrock.shared.http

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorsTest {

    private fun text(msg: String, data: String?) =
        serverErrorText(msg, data?.let { Json.parseToJsonElement(it) })

    @Test
    fun reason_is_appended_to_the_message() {
        assertEquals(
            "authentication_required (stale_hawk_timestamp)",
            text("authentication_required", """{"reason":"stale_hawk_timestamp","auth":"hawk"}"""),
        )
    }

    @Test
    fun reason_that_repeats_the_message_is_left_out() {
        assertEquals(
            "new_connections_disabled",
            text("new_connections_disabled", """{"reason":"new_connections_disabled","flow":"login"}"""),
        )
    }

    @Test
    fun missing_or_unusable_data_leaves_the_message_alone() {
        assertEquals("boom", text("boom", null))
        assertEquals("boom", text("boom", "null"))
        assertEquals("boom", text("boom", """{"error":"x"}"""))
        assertEquals("boom", text("boom", """["reason"]"""))
        assertEquals("boom", text("boom", """{"reason":42}"""))
        assertEquals("boom", text("boom", """{"reason":""}"""))
    }

    @Test
    fun reason_stands_in_for_an_empty_message() {
        assertEquals("invalid_hawk_mac", text("", """{"reason":"invalid_hawk_mac"}"""))
    }
}
