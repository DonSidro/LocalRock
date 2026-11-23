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
        // QX Revo Ultra 2 newFeatureSet: bits 23, 36, 40, 43, 53, 54, 56, 60, 63, 70, 74 and 118 are on, 58 is off.
        val set = "0000000000119418CCD57EFDA869B6DDDBFF8F7F7EFEFFFF"
        assertEquals(
            listOf(
                "avoid_collision", "carpet_first", "cleaning_pattern", "collision_mode", "corner_mop_stretch", "dirty_object_detect",
                "dust_collection_frequency", "gap_deep_clean", "optimize_battery", "pet_supplies_deep_clean", "right_brush_stretch",
                "water_leak_check",
            ),
            supportedBetaFeatures(set).filter { it.bit != null && it.bit != 3 && it.bit != 104 && it.bit != 105 && it.bit != 112 }.map { it.id }.sorted(),
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
            listOf("carpet_boost", "dust_collection_switch", "follow_ground_material", "identify_furniture", "identify_ground_material"),
            supportedBetaFeatures(null).map { it.id }.sorted(),
        )
    }

    @Test
    fun options_without_a_feature_bit_are_read_last() {
        val defs = supportedBetaFeatures("f".repeat(40))
        val order = betaReadOrder(defs)
        val firstUngated = order.indexOfFirst { it.bit == null }
        assertTrue(firstUngated > 0)
        assertTrue(order.drop(firstUngated).all { it.bit == null })
        assertTrue("carpet_boost" in order.drop(firstUngated).map { it.id })
        // Within each group the list's own order is kept.
        assertEquals(defs.filter { it.bit != null }, order.take(firstUngated))
        assertEquals(defs.filter { it.bit == null }, order.drop(firstUngated))
    }

    @Test
    fun uncleaned_area_reminder_needs_bit_154_and_climbing_arms_need_bit_147() {
        val bit154 = "4" + "0".repeat(38)
        val bit147 = "8" + "0".repeat(36)
        fun ids(set: String) = supportedBetaFeatures(set).map { it.id }
        assertTrue("uncleaned_area_reminder" in ids(bit154))
        assertFalse("climbing_arms_high_sill" in ids(bit154))
        assertTrue("climbing_arms_high_sill" in ids(bit147))
        assertFalse("uncleaned_area_reminder" in ids(bit147))
        assertFalse("uncleaned_area_reminder" in ids("0") || "climbing_arms_high_sill" in ids("0"))
    }

    @Test
    fun named_robot_settings_are_only_read_from_robots_that_advertise_them() {
        assertTrue(BetaFeatureDefs.filter { it.settingName != null }.all { it.bit != null })
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
    fun carpet_wire_avoidance_is_bit_105() {
        val on = "2" + "0".repeat(26)
        assertEquals(listOf("avoid_carpet_wires"), supportedBetaFeatures(on).filter { it.bit != null }.map { it.id })
        assertEquals(emptyList(), supportedBetaFeatures("0").filter { it.bit != null })
    }

    @Test
    fun wire_avoidance_is_a_named_robot_setting() {
        val def = BetaFeatureDefs.first { it.id == "avoid_carpet_wires" }
        assertEquals("nearbycarpet_detecting_wire_status", def.settingName)
        assertEquals("status" to 0, parseBetaReply(Json.parseToJsonElement("""{"status":0}""")))
    }

    @Test
    fun side_brush_lift_is_bit_104_and_one_means_on() {
        val on = "1" + "0".repeat(26)
        assertEquals(listOf("carpet_side_brush_lift"), supportedBetaFeatures(on).filter { it.bit != null }.map { it.id })
        val def = BetaFeatureDefs.first { it.id == "carpet_side_brush_lift" }
        assertEquals("disable_sidebrush_lift_status", def.settingName)
        // The name says disable but the plugin treats 1 as the switch being on.
        assertTrue(BetaFeatureState(def, value = 1, key = "status").enabled)
    }

    @Test
    fun set_tries_the_accepted_shape_first() {
        assertEquals(listOf(0, 1, 2), betaSetTryOrder(3, null))
        assertEquals(listOf(1, 0, 2), betaSetTryOrder(3, 1))
        assertEquals(listOf(2, 0, 1), betaSetTryOrder(3, 2))
        assertEquals(listOf(0), betaSetTryOrder(1, 2))
    }

    @Test
    fun both_param_error_codes_move_on_to_the_next_shape() {
        assertTrue(-10007 in BetaShapeRefusalCodes)
        assertTrue(-10005 in BetaShapeRefusalCodes)
        assertFalse(-10004 in BetaShapeRefusalCodes)
    }

    @Test
    fun advertised_features_without_a_switch_are_listed_by_name() {
        val set = "0000000000119418CCD57EFDA869B6DDDBFF8F7F7EFEFFFF"
        val listed = unlistedAdvertisedFeatures(set).map { it.name }
        assertFalse("Matter" in listed)
        assertTrue("Auto tear down mop" in listed)
        assertFalse("Gap deep clean" in listed)
        assertFalse("UVC sterilize" in listed)
        assertFalse("Mechanical arm mode" in listed)
    }

    @Test
    fun bits_with_wash_empty_and_carpet_controls_are_not_listed() {
        // Bits 41 (wash towel), 49 (carpet clean mode) and 53 (dust collection mode) have controls of their own.
        assertEquals(emptyList(), unlistedAdvertisedFeatures("2202" + "0".repeat(10)))
    }

    @Test
    fun bits_that_name_no_separate_setting_are_not_listed() {
        // Bits 38 (cloud endpoint), 42 (clean by floor type), 52 (AI recognition flag), 57 (no carpet pressure setting) and 62 (battery floor).
        assertEquals(emptyList(), unlistedAdvertisedFeatures("4210044000000000"))
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

    private val bit70 = "4" + "0".repeat(17)

    @Test
    fun cleaning_pattern_is_bit_70_and_a_named_setting() {
        assertEquals(listOf("cleaning_pattern"), supportedBetaFeatures(bit70).filter { it.bit != null }.map { it.id })
        assertEquals(emptyList(), supportedBetaFeatures("0").filter { it.id == "cleaning_pattern" })
        assertEquals("clean_direction_status", BetaFeatureDefs.first { it.id == "cleaning_pattern" }.settingName)
        assertEquals(emptyList(), unlistedAdvertisedFeatures(bit70).filter { it.bit == 70 })
    }

    @Test
    fun cleaning_pattern_replaces_the_floor_direction_toggle_only_once_it_answers() {
        val pattern = BetaFeatureDefs.first { it.id == "cleaning_pattern" }
        val toggle = BetaFeatureDefs.first { it.id == "follow_ground_material" }
        val both = listOf(BetaFeatureState(toggle, value = 1, key = "status"), BetaFeatureState(pattern, value = 0, key = "status"))
        assertEquals(listOf("cleaning_pattern"), both.withoutReplaced().map { it.def.id })
        val patternSilent = listOf(BetaFeatureState(toggle, value = 1, key = "status"))
        assertEquals(listOf("follow_ground_material"), patternSilent.withoutReplaced().map { it.def.id })
        assertTrue(supportedBetaFeatures(bit70).any { it.id == "follow_ground_material" })
    }

    @Test
    fun cleaning_pattern_values_map_to_labels() {
        val def = BetaFeatureDefs.first { it.id == "cleaning_pattern" }
        fun label(v: Int) = BetaFeatureState(def, value = v, key = "status").choiceLabel
        assertEquals("Smart", label(0))
        assertEquals("Along floor direction", label(1))
        assertEquals("Mixed pattern", label(2))
        assertEquals("Other (7)", label(7))
        assertNull(BetaFeatureState(def, value = null).choiceLabel)
    }

    @Test
    fun a_choice_setting_is_usable_for_any_number_but_a_switch_only_for_0_and_1() {
        val pattern = BetaFeatureDefs.first { it.id == "cleaning_pattern" }
        val switch = BetaFeatureDefs.first { it.id == "full_duplex" }
        assertTrue(BetaFeatureState(pattern, value = 2).toggleable)
        assertTrue(BetaFeatureState(pattern, value = 9).toggleable)
        assertFalse(BetaFeatureState(pattern, value = null).toggleable)
        assertFalse(BetaFeatureState(switch, value = 2).toggleable)
        assertNull(BetaFeatureState(switch, value = 1).choiceLabel)
    }
}
