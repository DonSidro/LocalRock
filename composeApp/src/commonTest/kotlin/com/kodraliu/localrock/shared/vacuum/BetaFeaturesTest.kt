package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class BetaFeaturesTest {

    @Test
    fun supported_features_follow_the_feature_bits() {
        // QX Revo Ultra 2 newFeatureSet: bits 36, 40, 43, 54, 56, 60 and 63 are on, 58 is off.
        val set = "0000000000119418CCD57EFDA869B6DDDBFF8F7F7EFEFFFF"
        assertEquals(
            listOf(
                "avoid_collision", "corner_mop_stretch", "dirty_object_detect", "gap_deep_clean",
                "pet_supplies_deep_clean", "right_brush_stretch", "water_leak_check",
            ),
            supportedBetaFeatures(set).filter { it.bit != null && it.bit != 3 && it.bit != 112 }.map { it.id }.sorted(),
        )
        assertEquals(emptyList(), supportedBetaFeatures("0").filter { it.bit != null })
    }

    @Test
    fun carpet_deep_clean_is_bit_3_and_cleaning_fluid_is_bit_112() {
        val carpet = "8"
        val fluid = "1" + "0".repeat(28)
        assertEquals(listOf("carpet_deep_clean"), supportedBetaFeatures(carpet).filter { it.bit != null }.map { it.id })
        assertEquals(listOf("clean_fluid_delivery"), supportedBetaFeatures(fluid).filter { it.bit != null }.map { it.id })
        assertEquals(emptyList(), supportedBetaFeatures("6" + "0".repeat(28)).filter { it.bit != null })
    }

    @Test
    fun features_without_a_known_bit_are_always_probed() {
        assertEquals(
            listOf("dust_collection_switch", "follow_ground_material", "identify_furniture", "identify_ground_material"),
            supportedBetaFeatures(null).map { it.id }.sorted(),
        )
    }

    @Test
    fun reply_shapes_parse_to_key_and_value() {
        assertEquals("status" to 1, parseBetaReply(Json.parseToJsonElement("""[{"status":1}]""")))
        assertEquals("status" to 0, parseBetaReply(Json.parseToJsonElement("""{"status":0}""")))
        assertEquals(null to 1, parseBetaReply(Json.parseToJsonElement("[1]")))
        assertEquals(null to 0, parseBetaReply(Json.parseToJsonElement("0")))
    }

    @Test
    fun unexpected_replies_do_not_parse() {
        assertNull(parseBetaReply(null))
        assertNull(parseBetaReply(Json.parseToJsonElement("""["ok"]""")))
        assertNull(parseBetaReply(Json.parseToJsonElement("""{"a":1,"b":2}""")))
        assertNull(parseBetaReply(Json.parseToJsonElement("[]")))
    }

    @Test
    fun set_params_try_the_reply_shape_then_the_other_forms() {
        assertEquals(
            listOf("""[{"status":1}]""", """{"status":1}""", "[1]"),
            betaSetParamCandidates("status", true).map { it.toString() },
        )
        assertEquals(listOf("[0]"), betaSetParamCandidates(null, false).map { it.toString() })
    }

    @Test
    fun advertised_features_without_a_switch_are_listed_by_name() {
        val set = "0000000000119418CCD57EFDA869B6DDDBFF8F7F7EFEFFFF"
        val listed = unlistedAdvertisedFeatures(set).map { it.name }
        assertTrue("Matter" in listed)
        assertTrue("Auto tear down mop" in listed)
        assertFalse("Gap deep clean" in listed)
        assertFalse("UVC sterilize" in listed)
        assertFalse("Mechanical arm mode" in listed)
    }

    @Test
    fun covered_and_handled_bits_are_not_listed() {
        // Bit 54 (FlexiArm side brush, has a switch) and bit 55 (SmartPlan, handled elsewhere).
        assertEquals(emptyList(), unlistedAdvertisedFeatures("C" + "0".repeat(13)))
    }

    @Test
    fun unnamed_bits_have_no_name_and_missing_sets_are_empty() {
        assertEquals(listOf(AdvertisedFeature(44, null)), unlistedAdvertisedFeatures("100000000000"))
        assertEquals(listOf(AdvertisedFeature(89, "Mechanical arm mode")), unlistedAdvertisedFeatures("2" + "0".repeat(22)))
        assertEquals(emptyList(), unlistedAdvertisedFeatures(null))
        assertEquals(emptyList(), unlistedAdvertisedFeatures(""))
    }
}
