package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settings the official app shows on a Qrevo 2 Pro (a294, 02.44.86) that read a field, some bits or a
 * multi-way value. The replies are what that robot answered on 2026-11-19, while the official app showed
 * Vacuum Carpet First on, Carpet Boost on, Less Collision Mode "High Coverage" and Dustbin Auto-Empty
 * Frequency "Balanced".
 */
class OfficialAppSettingsTest {
    private val qrevo2ProFlags = "0000009021d0b410c0c57efda82934c952ba8f587edefffe"

    private fun def(id: String) = BetaFeatureDefs.first { it.id == id }

    private fun read(id: String, reply: String): BetaFeatureState {
        val def = def(id)
        val parsed = parseFeatureReply(def, Json.parseToJsonElement(reply))
        return BetaFeatureState(def, value = parsed?.value, key = parsed?.key, reply = parsed?.reply)
    }

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun the_qrevo_flags_offer_the_new_settings() {
        val ids = supportedBetaFeatures(qrevo2ProFlags).map { it.id }.toSet()
        for (id in listOf(
            "carpet_first", "carpet_boost", "collision_mode", "dust_collection_frequency",
        )) {
            assertTrue(id in ids, "$id should be offered")
        }
    }

    @Test
    fun flag_gated_settings_stay_hidden_without_their_flag() {
        val ids = supportedBetaFeatures(null).map { it.id }.toSet()
        for (id in listOf("carpet_first", "collision_mode", "dust_collection_frequency")) {
            assertFalse(id in ids, "$id should not be offered")
        }
    }

    @Test
    fun vacuum_carpet_first_is_a_bit_of_switch_status() {
        val on = read("carpet_first", """[{"state":5,"switch_status":8,"collision_avoid_status":1}]""")
        assertTrue(on.toggleable)
        assertTrue(on.enabled)
        assertEquals(json("""{"status":0}"""), featureSetParamCandidates(on, 0).first())

        // 0x20 is cross carpet cleaning and 0x1 offline map: neither is Vacuum Carpet First.
        val off = read("carpet_first", """[{"switch_status":33}]""")
        assertTrue(off.toggleable)
        assertFalse(off.enabled)
        assertEquals(json("""{"status":1}"""), featureSetParamCandidates(off, 1).first())
    }

    @Test
    fun carpet_boost_writes_back_the_thresholds_it_read() {
        val state = read(
            "carpet_boost",
            """[{"enable":1,"current_integral":1000,"current_high":500,"current_low":1000,"stall_time":10}]""",
        )
        assertTrue(state.enabled)
        assertEquals(
            json("""[{"enable":0,"current_integral":1000,"current_high":500,"current_low":1000,"stall_time":10}]"""),
            featureSetParamCandidates(state, 0).first(),
        )
    }

    @Test
    fun less_collision_mode_is_a_two_way_picker() {
        val state = read("collision_mode", """{"status":1}""")
        assertTrue(state.toggleable)
        assertEquals("High coverage", state.choiceLabel)
        assertEquals(json("""{"status":2}"""), featureSetParamCandidates(state, 2).first())
        assertEquals("Other (0)", read("collision_mode", """{"status":0}""").choiceLabel)
    }

    @Test
    fun auto_empty_frequency_is_a_three_way_picker() {
        val state = read("dust_collection_frequency", """{"status":1}""")
        assertEquals("Balanced", state.choiceLabel)
        assertEquals(json("""{"status":2}"""), featureSetParamCandidates(state, 2).first())
        // The old switch cannot show a robot set to High.
        assertFalse(read("dust_collection_switch", """{"status":2}""").toggleable)
    }

    @Test
    fun the_pickers_replace_the_switches_they_read_the_same_value_as() {
        val shown = listOf(
            read("avoid_collision", """{"status":1}"""),
            read("collision_mode", """{"status":1}"""),
            read("dust_collection_switch", """{"status":1}"""),
            read("dust_collection_frequency", """{"status":1}"""),
        ).withoutReplaced().map { it.def.id }
        assertEquals(listOf("collision_mode", "dust_collection_frequency"), shown)
    }

    @Test
    fun a_robot_without_the_command_shows_nothing() {
        // An S7 answers commands it does not know with the bare string unknown_method.
        assertFalse(read("carpet_boost", "\"unknown_method\"").toggleable)
        assertNull(parseFeatureReply(def("carpet_first"), json("""[{"state":8}]""")))
    }
}
