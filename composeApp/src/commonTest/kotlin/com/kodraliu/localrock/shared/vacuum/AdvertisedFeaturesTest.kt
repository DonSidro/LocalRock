package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CURV = "00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"
private const val PRO = "42BA8F5C7EDAFFFE"

class AdvertisedFeaturesTest {
    private fun def(id: String) = BetaFeatureDefs.first { it.id == id }
    private fun json(s: String) = Json.parseToJsonElement(s)
    private fun ids(set: String) = supportedBetaFeatures(set).map { it.id }

    @Test
    fun curv_unlisted_list_is_only_the_unknown_bits() {
        val unlisted = unlistedAdvertisedFeatures(CURV)
        assertEquals(listOf(59, 68, 95, 103, 123, 127), unlisted.map { it.bit })
        assertEquals("Unknown (59)", unlisted.first().name)
        assertTrue(unlisted.all { it.name == "Unknown (${it.bit})" })
    }

    @Test
    fun pro_has_nothing_unlisted_once_re_mopping_is_a_switch() {
        assertEquals(emptyList(), unlistedAdvertisedFeatures(PRO))
        assertTrue("auto_remop" in ids(PRO))
    }

    @Test
    fun new_controls_gate_on_their_bits() {
        val curv = ids(CURV)
        for (id in listOf("auto_remop", "voice_assistant", "pet_snapshot", "identify_room", "drying_start_method", "less_collision_mode")) {
            assertTrue(id in curv, id)
        }
        val pro = ids(PRO)
        for (id in listOf("voice_assistant", "pet_snapshot", "identify_room", "drying_start_method", "less_collision_mode")) {
            assertFalse(id in pro, id)
        }
    }

    @Test
    fun two_gear_robots_get_the_picker_and_others_keep_the_switch() {
        assertFalse("avoid_collision" in ids(CURV))
        assertTrue("avoid_collision" in ids(PRO))
        assertEquals(listOf(1, 2), def("less_collision_mode").choices!!.map { it.value })
        assertEquals(listOf("High Coverage", "Less Collision"), def("less_collision_mode").choices!!.map { it.label })
    }

    @Test
    fun less_collision_writes_the_bare_object_first_and_never_needs_zero() {
        val shapes = featureSetCandidates(def("less_collision_mode"), "status", 2).map { it.toString() }
        assertEquals(listOf("""{"status":2}""", """[{"status":2}]""", "[2]"), shapes)
    }

    @Test
    fun side_brush_on_carpet_is_hidden_on_carpet_shape_robots() {
        assertFalse("carpet_side_brush_lift" in ids(CURV))
        assertTrue("carpet_side_brush_lift" in ids("1" + "0".repeat(26)))
    }

    @Test
    fun debug_switches_stay_hidden_but_count_as_known() {
        assertFalse("common_obs_avoidance" in ids(CURV))
        assertFalse("object_detect_avoidance" in ids(CURV))
        assertTrue(unlistedAdvertisedFeatures(CURV).none { it.bit == 64 })
    }

    @Test
    fun voice_assistant_tries_the_bare_object_first() {
        val shapes = featureSetCandidates(def("voice_assistant"), "data", 0).map { it.toString() }
        assertEquals("""{"data":0}""", shapes.first())
        assertEquals("data" to 1, parseFeatureReply(def("voice_assistant"), json("""{"data":1}""")))
    }

    @Test
    fun re_mopping_reads_switch_status_from_get_prop() {
        val d = def("auto_remop")
        assertEquals("status" to 1, parseFeatureReply(d, json("""[{"state":8,"switch_status":17}]""")))
        assertEquals("status" to 0, parseFeatureReply(d, json("""[{"state":8,"switch_status":1}]""")))
        assertEquals("status" to 0, parseFeatureReply(d, json("""[{"switch_status":9}]""")))
        assertNull(parseFeatureReply(d, json("""[{"state":8}]""")))
        assertEquals(listOf("""{"status":1}"""), featureSetCandidates(d, "status", 1).map { it.toString() })
        assertEquals("app_set_dirty_replenish_clean_status", d.setMethod)
    }

    @Test
    fun pet_snaps_is_camera_status_bit_14_and_needs_pet_mode_and_photos() {
        val d = def("pet_snapshot")
        assertEquals(null to 1, parseFeatureReply(d, json("[28071]")))
        assertEquals(null to 0, parseFeatureReply(d, json("[11687]")))
        assertEquals(11687, cameraWordWith(28071, 14, false))
        assertEquals(28071, cameraWordWith(11687, 14, true))
        assertNull(BetaFeatureState(d, value = 0, raw = "[11687]").blockedReason)
        // Pet mode (bit 1) off.
        assertEquals("Enable Obstacle Photos and Pet mode first", BetaFeatureState(d, value = 0, raw = "[${11687 and 2.inv()}]").blockedReason)
    }

    @Test
    fun obstacle_camera_rows_read_their_bits_of_the_curv_word() {
        // 28071 is the Curv 2 Flow's word with avoidance, photos, pet and snaps on and the fill light Automatic.
        for (id in listOf("obstacle_avoidance", "obstacle_photos", "pet_mode", "pet_snapshot")) {
            assertEquals(null to 1, parseFeatureReply(def(id), json("[28071]")), id)
        }
        assertEquals(null to 0, parseFeatureReply(def("fill_light"), json("[28071]")))
        assertEquals(null to 2, parseFeatureReply(def("fill_light"), json("[${28071 or (2 shl 3)}]")))
        assertEquals("Automatic", BetaFeatureState(def("fill_light"), value = 0).choiceLabel)
        assertEquals("Off", BetaFeatureState(def("fill_light"), value = 2).choiceLabel)
    }

    @Test
    fun turning_a_camera_bit_off_clears_the_bits_that_depend_on_it() {
        fun word(id: String, on: Boolean, from: Int = 28071) = cameraWordFor(def(id).codec as FeatureCodec.CameraStatusBit, from, on)
        // Avoidance off also clears exploration (6), photos (10) and pet snaps (14); pet mode (1) stays.
        assertEquals(28071 and (1 or (1 shl 6) or (1 shl 10) or (1 shl 14)).inv(), word("obstacle_avoidance", false))
        assertEquals(28071 and ((1 shl 10) or (1 shl 14)).inv(), word("obstacle_photos", false))
        assertEquals(28071 and (2 or (1 shl 14)).inv(), word("pet_mode", false))
        assertEquals(11687, word("pet_snapshot", false))
        assertEquals(28071, word("obstacle_avoidance", true, from = 28070))
    }

    @Test
    fun photos_and_pet_mode_need_avoidance_on_first() {
        val cameraOff = 28071 and 1.inv()
        for (id in listOf("obstacle_photos", "pet_mode")) {
            assertEquals("Enable Reactive Obstacle Avoidance", BetaFeatureState(def(id), value = 0, raw = "[${cameraOff and (2 or (1 shl 10)).inv()}]").blockedReason, id)
            assertNull(BetaFeatureState(def(id), value = 0, raw = "[${28071 and (2 or (1 shl 10)).inv()}]").blockedReason, id)
        }
        assertNull(BetaFeatureState(def("obstacle_avoidance"), value = 0, raw = "[0]").blockedReason)
    }

    @Test
    fun fill_light_writes_only_bits_3_and_4() {
        val c = def("fill_light").codec as FeatureCodec.CameraStatusField
        assertEquals(28071 or (2 shl 3), cameraWordWithField(c, 28071, 2))
        assertEquals(28071, cameraWordWithField(c, 28071 or (3 shl 3), 0))
        // A value wider than the field cannot spill into bit 5.
        assertEquals(28071 or (3 shl 3), cameraWordWithField(c, 28071, 7))
    }

    @Test
    fun fill_light_needs_a_video_camera_bit() {
        assertTrue("fill_light" in ids(CURV))
        assertFalse("fill_light" in ids(PRO))
        assertTrue("obstacle_avoidance" in ids(PRO))
        for (id in listOf("obstacle_photos", "pet_mode")) {
            assertFalse(id in ids(PRO), id)
            assertTrue(id in ids(CURV), id)
        }
    }

    @Test
    fun ai_environment_is_on_when_either_read_is_on_and_needs_both_reads() {
        val d = def("ai_environment")
        val on = json("""[{"status":1}]""")
        val off = json("""{"status":0}""")
        assertEquals("status" to 1, parseFeatureReply(d, aiEnvironmentAnswer(on, on, null)))
        assertEquals("status" to 1, parseFeatureReply(d, aiEnvironmentAnswer(off, on, null)))
        assertEquals("status" to 0, parseFeatureReply(d, aiEnvironmentAnswer(off, off, null)))
        assertEquals("status" to 1, parseFeatureReply(d, aiEnvironmentAnswer(off, off, on)))
        assertNull(parseFeatureReply(d, aiEnvironmentAnswer(on, null, null)))
        assertNull(parseFeatureReply(d, aiEnvironmentAnswer(json("[]"), on, null)))
    }

    @Test
    fun ai_environment_adds_room_type_only_on_bit_66_robots() {
        val curv = supportedBetaFeatures(CURV).first { it.id == "ai_environment" }
        assertEquals(FeatureCodec.AiEnvironment(withRoomType = true), curv.codec)
        assertTrue(("app_get_robot_setting" to "identify_room_status") in featureReads(curv))
        val pro = supportedBetaFeatures(PRO).first { it.id == "ai_environment" }
        assertEquals(FeatureCodec.AiEnvironment(withRoomType = false), pro.codec)
        assertEquals(setOf<Pair<String, String?>>("get_identify_ground_material_status" to null, "get_identify_furniture_status" to null), featureReads(pro))
        // The floor page's ground material row shares the read, so a toggle refreshes it too.
        assertTrue(featureReads(def("identify_ground_material")).any { it in featureReads(pro) })
    }

    @Test
    fun camera_rows_share_one_read() {
        val reads = featureReads(def("obstacle_avoidance"))
        assertTrue(listOf("obstacle_photos", "pet_mode", "pet_snapshot", "fill_light").all { featureReads(def(it)) == reads })
        assertFalse(featureReads(def("identify_room")).any { it in featureReads(def("auto_remop")) })
    }

    @Test
    fun drying_start_method_reads_status_and_writes_the_minimal_body() {
        val d = def("drying_start_method")
        val answer = json("""{"status":1,"on":{"cliff_on":1000,"dry_time":14400},"off":{"count":10},"cron":"42 21 * * *"}""") as JsonObject
        assertEquals("status" to 1, parseFeatureReply(d, answer))
        assertEquals("""{"on":{"dry_time":14400},"status":0,"cron":"42 21 * * *"}""", dryerStartMethodBody(answer, 0).toString())
        val noCron = json("""{"status":1,"on":{"dry_time":10800},"cron":""}""") as JsonObject
        assertEquals("""{"on":{"dry_time":10800},"status":2,"cron":"0 3 * * *"}""", dryerStartMethodBody(noCron, 2).toString())
        assertEquals("""{"status":1,"cron":""}""", dryerStartMethodBody(null, 1).toString())
        assertEquals(listOf(0, 1, 2), d.choices!!.map { it.value })
    }

    @Test
    fun named_settings_write_one_shape() {
        assertEquals(
            listOf("""{"name":"identify_room_status","status":0}"""),
            featureSetCandidates(def("identify_room"), "status", 0).map { it.toString() },
        )
    }

    @Test
    fun plain_switches_keep_the_old_shape_order() {
        assertEquals(
            betaSetParamCandidates("status", true),
            featureSetCandidates(def("optimize_battery"), "status", 1),
        )
    }

    @Test
    fun advertised_flags_follow_the_bits() {
        val curv = buildAdvertisedCapabilities(CURV)
        assertTrue(curv.cleanThenMop && curv.cleanThenMopRepeat && curv.cleanTimeline && curv.mapEraser && curv.syncRoomNames)
        assertTrue(curv.midwayDock && curv.dustCollectionCount && curv.rollerMop && curv.dirtyTankCleanState)
        assertTrue(curv.cleanEfficiency && curv.forbidPureMop && curv.noMopAvoidStroll && curv.carpetShapes && curv.floorPlanMap && curv.twoGearCollision)
        assertEquals(AdvertisedCapabilities(), buildAdvertisedCapabilities(PRO))
    }
}
