package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarpetBoostTest {
    private val def = BetaFeatureDefs.first { it.id == "carpet_boost" }
    private val reply = Json.parseToJsonElement(
        """[{"enable":1,"current_integral":1000,"current_high":500,"current_low":1000,"stall_time":10}]""",
    )

    @Test
    fun the_switch_reads_the_enable_field() {
        assertEquals("enable" to 1, parseFeatureReply(def, reply))
        assertEquals("enable" to 0, parseFeatureReply(def, Json.parseToJsonElement("""{"enable":0,"stall_time":10}""")))
        assertNull(parseFeatureReply(def, Json.parseToJsonElement("""[{"stall_time":10}]""")))
    }

    @Test
    fun the_write_sends_the_whole_object_back_with_only_enable_changed() {
        val reported = (reply as JsonArray)[0].jsonObject
        val params = carpetModeSetParams(reported, 0)
        assertEquals(1, params.size)
        val body = params[0].jsonObject
        assertEquals(0, body["enable"]!!.jsonPrimitive.int)
        assertEquals(reported.filterKeys { it != "enable" }, body.filterKeys { it != "enable" })
        assertEquals(reported.keys.toList(), body.keys.toList())
    }

    @Test
    fun carpet_first_and_cross_read_their_switch_status_bits_and_write_status() {
        val first = BetaFeatureDefs.first { it.id == "carpet_first" }
        val cross = BetaFeatureDefs.first { it.id == CARPET_CROSS_ID }
        assertEquals(23, first.bit)
        assertEquals(49, cross.bit)
        // switch_status 40 = bit 3 (Carpet First) and bit 5 (Cross); 8 = Carpet First only.
        val both = Json.parseToJsonElement("""[{"state":8,"switch_status":40}]""")
        val firstOnly = Json.parseToJsonElement("""[{"state":8,"switch_status":8}]""")
        assertEquals("status" to 1, parseFeatureReply(first, both))
        assertEquals("status" to 1, parseFeatureReply(cross, both))
        assertEquals("status" to 0, parseFeatureReply(cross, firstOnly))
        assertEquals("app_set_priority_carpet_cleaning_status", first.setMethod)
        assertEquals("app_set_cross_carpet_cleaning_status", cross.setMethod)
        assertEquals(listOf(Json.parseToJsonElement("""{"status":1}""")), featureSetCandidates(cross, "status", 1))
    }

    @Test
    fun the_row_is_on_the_carpet_page_for_every_robot_after_carpet_first() {
        assertNull(def.bit)
        assertEquals(FeatureSection.CARPET, def.section)
        assertEquals("get_carpet_mode", def.getMethod)
        assertEquals("set_carpet_mode", def.setMethod)
        val carpet = BetaFeatureDefs.filter { it.section == FeatureSection.CARPET }.map { it.id }
        assertEquals(listOf("carpet_first", CARPET_CROSS_ID, "carpet_boost", "carpet_deep_clean", "avoid_carpet_wires", "carpet_side_brush_lift"), carpet)
    }
}
