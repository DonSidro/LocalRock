package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerTimerTest {

    @Test
    fun get_server_timer_answer_parses_into_thin_entries() {
        // Captured live from a Roborock Qrevo.
        val answer = Json.parseToJsonElement(
            """[["1759965363822","on",-1],["1759965417864","on",-1],["1759965452001","off",-1],["1792275311210","on",-1]]""",
        )

        val entries = parseServerTimerList(answer)

        assertEquals(4, entries.size)
        assertEquals(ServerTimerEntry("1759965363822", enabled = true, mapIndex = -1), entries[0])
        assertEquals(ServerTimerEntry("1759965452001", enabled = false, mapIndex = -1), entries[2])
    }

    @Test
    fun malformed_entries_are_skipped_and_a_non_list_is_empty() {
        val answer = Json.parseToJsonElement("""[["only-an-id"], "junk", ["3","on",-1]]""")

        assertEquals(listOf(ServerTimerEntry("3", enabled = true, mapIndex = -1)), parseServerTimerList(answer))
        assertEquals(emptyList(), parseServerTimerList(Json.parseToJsonElement("""{"status":1}""")))
        assertEquals(emptyList(), parseServerTimerList(null))
    }

    @Test
    fun set_server_timer_params_match_the_captured_wire_format() {
        val settings = TimerSettings(
            repeat = 1, waterBoxMode = 202, segments = "0", mopMode = 300, fanPower = 102, cleanMop = 1, mapIndex = -1,
        )

        val params = serverTimerSetParams("1792275311210", "15 23 * * 0,1,2,3,4,5,6", settings)

        assertEquals(
            """{"data":[["1792275311210",["15 23 * * 0,1,2,3,4,5,6",["start_clean",""" +
                """{"fan_power":102,"water_box_mode":202,"mop_mode":300,"repeat":1,"segments":"0",""" +
                """"clean_mop":1,"map_index":-1}]]]],"need_retry":1}""",
            params.toString(),
        )
    }

    @Test
    fun usesServerTimer_reads_bit_0_of_the_featureset_the_same_way_the_official_app_does() {
        fun initStatus(featureset: Int) = Json.parseToJsonElement(
            """[{"local_info":{"featureset":$featureset}}]""",
        )

        assertEquals(true, usesServerTimer(initStatus(1)))
        assertEquals(true, usesServerTimer(initStatus(3))) // other bits set too; only bit 0 matters
        assertEquals(false, usesServerTimer(initStatus(0)))
        assertEquals(false, usesServerTimer(initStatus(2)))
    }

    @Test
    fun usesServerTimer_is_null_when_the_answer_cannot_be_read() {
        assertNull(usesServerTimer(null))
        assertNull(usesServerTimer(Json.parseToJsonElement("[]")))
        assertNull(usesServerTimer(Json.parseToJsonElement("""[{"no_local_info": true}]""")))
    }
}
