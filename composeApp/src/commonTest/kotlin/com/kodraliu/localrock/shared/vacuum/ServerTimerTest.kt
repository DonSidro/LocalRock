package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
    fun a_schedule_that_leaves_fields_open_is_completed_the_way_the_official_app_sends_it() {
        // Only the three mode codes: the robot answered "Param error" to exactly this.
        val smart = TimerSettings(fanPower = 110, waterBoxMode = 209, mopMode = 306)

        assertEquals(
            TimerSettings(
                fanPower = 110, waterBoxMode = 209, mopMode = 306, repeat = 1, segments = "0", cleanMop = 1, mapIndex = -1,
            ),
            smart.completedForServerTimer(),
        )
        // An old schedule that carried no modes gets plain defaults.
        assertEquals(
            TimerSettings(
                fanPower = 102, waterBoxMode = 202, mopMode = 300, repeat = 1, segments = "0", cleanMop = 1, mapIndex = -1,
            ),
            TimerSettings().completedForServerTimer(),
        )
        // What the schedule chose is never overwritten.
        assertEquals(2, TimerSettings(repeat = 2).completedForServerTimer().repeat)
    }

    @Test
    fun only_a_timer_type_error_sends_a_save_to_the_other_protocol() {
        // Both captured live, both code -10005.
        val typeError = Json.parseToJsonElement("""{"code":-10005,"message":"timer type error"}""") as kotlinx.serialization.json.JsonObject
        val paramError = Json.parseToJsonElement("""{"code":-10005,"message":"Param error"}""") as kotlinx.serialization.json.JsonObject

        assertEquals(true, isTimerTypeError(typeError))
        assertEquals(false, isTimerTypeError(paramError))
        assertEquals(false, isTimerTypeError(Json.parseToJsonElement("""{"code":-1,"message":"timer type error"}""") as kotlinx.serialization.json.JsonObject))
    }

    @Test
    fun malformed_entries_are_skipped_and_a_non_list_is_empty() {
        val answer = Json.parseToJsonElement("""[["only-an-id"], "junk", ["3","on",-1]]""")

        assertEquals(listOf(ServerTimerEntry("3", enabled = true, mapIndex = -1)), parseServerTimerList(answer))
        assertEquals(emptyList(), parseServerTimerList(Json.parseToJsonElement("""{"status":1}""")))
        assertEquals(emptyList(), parseServerTimerList(null))
    }

    @Test
    fun upd_server_timer_params_match_the_captured_wire_format() {
        // Captured from the official app turning a schedule off and back on; the robot answered ["ok"].
        assertEquals("""[["1792291871087","off"]]""", JsonArray(serverTimerUpdParams("1792291871087", false)).toString())
        assertEquals("""[["1792291871087","on"]]""", JsonArray(serverTimerUpdParams("1792291871087", true)).toString())
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
