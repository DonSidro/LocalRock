package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.ZoneKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every expected string below is copied verbatim from a capture of the official Roborock app
 * editing the S8 Pro Ultra through local_roborock_server (decompiled_mqtt.jsonl, 2026-09-28).
 */
class MapEditCommandsTest {

    private val wallA = VirtualWall(24433, 18882, 22220, 16918)
    private val wallB = VirtualWall(29245, 23791, 29256, 22326)
    private val noGo = MapZone(30233, 24477, 31623, 24477, 31623, 23066, 30233, 23066, ZoneKind.NO_GO)

    @Test
    fun save_map_walls_only_matches_capture() {
        assertEquals(
            """{"data":[[1,24433,18882,22220,16918],[1,29245,23791,29256,22326],[100,0]],"need_retry":1}""",
            encodeSaveMapParams(emptyList(), listOf(wallA, wallB), mapIndex = 0).toString(),
        )
    }

    @Test
    fun save_map_walls_and_no_go_matches_capture() {
        assertEquals(
            """{"data":[[1,24433,18882,22220,16918],[1,29245,23791,29256,22326],""" +
                """[0,30233,24477,31623,24477,31623,23066,30233,23066],[100,0]],"need_retry":1}""",
            encodeSaveMapParams(listOf(noGo), listOf(wallA, wallB), mapIndex = 0).toString(),
        )
    }

    @Test
    fun editor_rectangles_use_the_captured_corner_order() {
        // The official app's no-go zone: top-left, top-right, bottom-right, bottom-left (Y up).
        assertEquals(noGo, MapZone.rect(31623, 23066, 30233, 24477, ZoneKind.NO_GO))
    }

    @Test
    fun save_map_names_the_robots_only_map_whatever_its_index() {
        // A Qrevo S Pro kept its only map as map 1 and accepted [100, 1] (issue #88).
        assertTrue(saveMapData(listOf(noGo), emptyList(), mapIndex = 1).toString().endsWith("[100,1]]"))
    }

    @Test
    fun save_map_enforces_the_68_vertex_limit_and_map_bounds() {
        val zones = List(17) { noGo }
        encodeSaveMapParams(zones, emptyList(), 0)
        assertFailsWith<MapEditException> { encodeSaveMapParams(zones, listOf(wallA), 0) }
        assertFailsWith<MapEditException> { encodeSaveMapParams(emptyList(), listOf(VirtualWall(-1, 0, 10, 10)), 0) }
        assertFailsWith<MapEditException> { encodeSaveMapParams(emptyList(), listOf(VirtualWall(0, 0, 51_201, 10)), 0) }
    }

    @Test
    fun split_segment_matches_capture() {
        assertEquals(
            """{"data":[23,24950,21350,24950,20500],"need_retry":1}""",
            encodeSplitSegmentParams(23, 24950, 21350, 24950, 20500).toString(),
        )
    }

    // Third capture (19:57-20:00 UTC): adding a second carpet/threshold re-sent the first one,
    // deleting one sent the rest, so both commands carry the complete set.
    private val carpetA = MapZone(30660, 24711, 32317, 24711, 32317, 23109, 30660, 23109, ZoneKind.CARPET)
    private val carpetB = MapZone(25603, 21237, 28555, 21237, 28555, 18284, 25603, 18284, ZoneKind.CARPET)
    private val thresholdA = MapZone(30294, 26592, 30281, 25684, 30167, 25685, 30180, 26593, ZoneKind.THRESHOLD)
    private val thresholdB = MapZone(26240, 17598, 27347, 16642, 27273, 16556, 26165, 17512, ZoneKind.THRESHOLD)

    @Test
    fun carpet_areas_match_capture() {
        assertEquals(
            """{"map_index":0,"type":1,"zone_data":[[30660,24711,32317,24711,32317,23109,30660,23109],""" +
                """[25603,21237,28555,21237,28555,18284,25603,18284]]}""",
            encodeCarpetAreaParams(listOf(carpetA, carpetB), 0, withFlags = false).toString(),
        )
        assertEquals(
            """{"map_index":0,"type":1,"zone_data":[[30660,24711,32317,24711,32317,23109,30660,23109]]}""",
            encodeCarpetAreaParams(listOf(carpetA), 0, withFlags = false).toString(),
        )
        assertTrue(encodeCarpetAreaParams(listOf(carpetA), 1, withFlags = false).toString().startsWith("{\"map_index\":1,"))
    }

    @Test
    fun thresholds_match_capture() {
        assertEquals(
            """{"map_index":0,"sides_infos":[[0,0,0,0],[0,0,0,0]],"zones":""" +
                """[[2,30294,26592,30281,25684,30167,25685,30180,26593],[2,26240,17598,27347,16642,27273,16556,26165,17512]]}""",
            encodeThresholdParams(listOf(thresholdA, thresholdB), 0).toString(),
        )
        assertEquals(
            """{"map_index":0,"sides_infos":[[0,0,0,0]],"zones":[[2,30294,26592,30281,25684,30167,25685,30180,26593]]}""",
            encodeThresholdParams(listOf(thresholdA), 0).toString(),
        )
    }

    @Test
    fun threshold_strip_rebuilds_the_official_apps_thresholds() {
        // Centre lines of the two captured strips (midpoints of the short ends).
        fun rebuilt(t: MapZone) = thresholdStrip((t.x0 + t.x3) / 2 to (t.y0 + t.y3) / 2, (t.x1 + t.x2) / 2 to (t.y1 + t.y2) / 2)
        for (t in listOf(thresholdA, thresholdB)) {
            val r = rebuilt(t)
            val expected = listOf(t.x0, t.y0, t.x1, t.y1, t.x2, t.y2, t.x3, t.y3)
            val actual = listOf(r.x0, r.y0, r.x1, r.y1, r.x2, r.y2, r.x3, r.y3)
            // Same corner order; within 1 mm (integer midpoints).
            expected.zip(actual).forEach { (e, a) -> assertTrue(kotlin.math.abs(e - a) <= 1, "expected $expected got $actual") }
            assertEquals(ZoneKind.THRESHOLD, r.kind)
        }
        assertFailsWith<MapEditException> { thresholdStrip(100 to 100, 100 to 100) }
    }

    @Test
    fun merge_segment_matches_capture() {
        // Official app, 19:46:35: {"data":[18,27],"need_retry":1} -> ["retry"] -> ["ok"].
        assertEquals("""{"data":[18,27],"need_retry":1}""", encodeMergeSegmentParams(18, 27).toString())
    }

    @Test
    fun split_sends_the_accepted_line_order_first_and_the_reverse_second() {
        // Refused at 19:45:47: [23,25050,21650,25050,20200]. Accepted at 19:46:24: [23,25050,20200,25050,21650].
        val refused = (25050 to 21650) to (25050 to 20200)
        val accepted = (25050 to 20200) to (25050 to 21650)
        assertEquals(listOf(accepted, refused), splitLineAttempts(refused.first, refused.second))
        assertEquals(listOf(accepted, refused), splitLineAttempts(accepted.first, accepted.second))
        // Equal Y: the smaller X goes first.
        assertEquals((100 to 500) to (900 to 500), splitLineAttempts(900 to 500, 100 to 500).first())
    }

    @Test
    fun split_failure_code_is_read_from_the_robot_error() {
        val error = Json.parseToJsonElement("""{"code":-10006,"message":"Split map failed"}""") as JsonObject
        assertEquals(SPLIT_FAILED_CODE, RobotCommandException("split_segment", error).code())
    }

    @Test
    fun ground_material_matches_capture() {
        // The capture set two rooms in one call: {"data":[[21,4,-1],[24,3,90]]}.
        assertEquals("""{"data":[[21,4,-1]]}""", encodeGroundMaterialParams(21, 4, null).toString())
        assertEquals("""{"data":[[24,3,90]]}""", encodeGroundMaterialParams(24, 3, 90).toString())
        assertEquals("""{"data":[[16,0,-1]]}""", encodeGroundMaterialParams(16, 0, null).toString())
    }

    @Test
    fun name_segment_matches_capture() {
        // The captured table, except that segment 24 carries a real room id. (The official app
        // stored a JSON fragment there, see the note in the repository.)
        val table = listOf(
            SegmentEntry(16, "12916445", 2), SegmentEntry(17, "12916459", 14),
            SegmentEntry(19, "22335520", 3), SegmentEntry(20, "12916442", 3),
            SegmentEntry(22, "22335410", 12), SegmentEntry(23, "22335552", 13),
            SegmentEntry(24, "47357674", 12), SegmentEntry(25, "33499526", 10),
            SegmentEntry(26, "12916427", 6),
        )
        assertEquals(
            """{"data":[{"iotRoomId":"12916445","robotRoomId":16,"robotTagId":2},""" +
                """{"iotRoomId":"12916459","robotRoomId":17,"robotTagId":14},""" +
                """{"iotRoomId":"22335520","robotRoomId":19,"robotTagId":3},""" +
                """{"iotRoomId":"12916442","robotRoomId":20,"robotTagId":3},""" +
                """{"iotRoomId":"22335410","robotRoomId":22,"robotTagId":12},""" +
                """{"iotRoomId":"22335552","robotRoomId":23,"robotTagId":13},""" +
                """{"iotRoomId":"47357674","robotRoomId":24,"robotTagId":12},""" +
                """{"iotRoomId":"33499526","robotRoomId":25,"robotTagId":10},""" +
                """{"iotRoomId":"12916427","robotRoomId":26,"robotTagId":6}],"need_retry":1}""",
            encodeNameSegmentParams(table.shuffled()).toString(),
        )
    }

    @Test
    fun name_segment_final_answer_is_parsed() {
        val result = Json.parseToJsonElement("""[{"oldId":16,"newId":16},{"oldId":24,"newId":27}]""")
        assertEquals(mapOf(16 to 16, 24 to 27), parseSegmentRenumbering(result))
        assertEquals(emptyMap(), parseSegmentRenumbering(Json.parseToJsonElement("""["ok"]""")))
    }

    @Test
    fun segment_table_is_parsed() {
        val result = Json.parseToJsonElement("""[[16,"33499526",14],[17,"12916427",6]]""")
        assertEquals(
            listOf(SegmentEntry(16, "33499526", 14), SegmentEntry(17, "12916427", 6)),
            parseSegmentTable(result),
        )
    }

    @Test
    fun empty_segment_table_is_kept_apart_from_an_unreadable_one() {
        // S7 Max Ultra and Qrevo Edge answer [] before any room has been named (#33).
        assertEquals(emptyList(), parseSegmentTable(Json.parseToJsonElement("[]")))
        assertNull(parseSegmentTable(null))
        assertNull(parseSegmentTable(Json.parseToJsonElement("""["ok"]""")))
        // One row this app can't write back makes the whole table unsafe to send.
        assertNull(parseSegmentTable(Json.parseToJsonElement("""[[16,"33499526",14],[17,"12916427"]]""")))
    }

    @Test
    fun provisional_retry_is_recognised() {
        fun resp(result: String?, error: String? = null) = V1Response(
            id = 2107,
            result = result?.let { Json.parseToJsonElement(it) },
            error = error?.let { Json.parseToJsonElement(it).jsonObject },
        )
        assertTrue(resp("""["retry"]""").isProvisionalRetry())
        assertFalse(resp("""["ok"]""").isProvisionalRetry())
        assertFalse(resp("""[{"oldId":16,"newId":16}]""").isProvisionalRetry())
        assertFalse(resp(null, """{"code":-10006,"message":"Split map failed"}""").isProvisionalRetry())
    }

    @Test
    fun a_refused_split_says_to_draw_the_line_across_the_whole_room() {
        val refused = Json.parseToJsonElement("""{"code":-10006,"message":"Split map failed"}""") as JsonObject
        val shown = splitFailure(RobotCommandException("split_segment", refused))
        assertTrue(shown is MapEditException)
        assertEquals(SPLIT_REFUSED_MESSAGE, shown.message)
        assertTrue(SPLIT_REFUSED_MESSAGE.contains("all the way across the room"))
    }

    @Test
    fun other_split_errors_are_left_as_the_robot_said_them() {
        val other = Json.parseToJsonElement("""{"code":-10005,"message":"Param is not an array"}""") as JsonObject
        val error = RobotCommandException("split_segment", other)
        assertEquals(error, splitFailure(error))
    }

    @Test
    fun robot_errors_read_well() {
        val error = Json.parseToJsonElement("""{"code":-10006,"message":"Split map failed"}""") as JsonObject
        assertEquals(
            "The robot refused split_segment: Split map failed (code -10006)",
            RobotCommandException("split_segment", error).message,
        )
    }

    @Test
    fun save_blocker() {
        fun map(noMop: List<MapZone> = emptyList(), blocks: Set<Int> = emptySet()) = ParsedMap(
            width = 1, height = 1, grid = ByteArray(1), rooms = emptyList(), resolution = 0.05f,
            noMopZones = noMop, unmodeledRestrictionBlocks = blocks, format = MapFormat.LEGACY,
        )
        assertNull(restrictionSaveBlocker(map(), mapIndex = 0))
        // Cliffs and smart door sills have their own storage; save_map never carries them.
        assertNull(restrictionSaveBlocker(map(blocks = setOf(19, 30, 31)), mapIndex = 0))
        assertTrue(restrictionSaveBlocker(map(blocks = setOf(23)), 0)!!.contains("mop-only"))
        assertTrue(restrictionSaveBlocker(map(noMop = listOf(noGo.copy(kind = ZoneKind.NO_MOP))), 0)!!.contains("no-mop"))
        assertNull(restrictionSaveBlocker(map(), mapIndex = 1))
        assertTrue(restrictionSaveBlocker(map(), mapIndex = null)!!.contains("single saved map"))
    }

    @Test
    fun save_map_index_comes_from_a_single_map_list() {
        // The capture's get_multi_maps_list: one map, "Hus", mapFlag 0.
        assertEquals(0, saveMapIndex(listOf(FloorMap("Hus", 0))))
        assertEquals(1, saveMapIndex(listOf(FloorMap("Home", 1))))
        assertNull(saveMapIndex(emptyList()))
        assertNull(saveMapIndex(listOf(FloorMap("Down", 0), FloorMap("Up", 1))))
    }

    @Test
    fun backup_and_restore_name_the_map_by_its_flag() {
        // manual_bak_map and recover_multi_map, captured on the S8 2026-10-28.
        assertEquals("""{"map_flag":0}""", encodeMapFlagParams(0).toString())
        assertEquals("""{"map_flag":2}""", encodeMapFlagParams(2).toString())
    }

    @Test
    fun saved_map_and_backup_requests_match_capture() {
        // get_multi_map as the official app sent it for the map list and the backup preview.
        assertEquals("""{"map_index":0}""", savedMapParams(0, backup = false).toString())
        assertEquals("""{"is_bak":1,"map_index":0}""", savedMapParams(0, backup = true).toString())
    }

    @Test
    fun map_list_carries_each_maps_backup_time() {
        // get_multi_maps_list from the S8 2026-10-28, after "Back up now" on "Hus".
        val mapInfo = Json.parseToJsonElement(
            """[{"mapFlag": 0, "add_time": 1792948453, "length": 3, "name": "Hus", "bak_maps": [{"mapFlag": 4, "add_time": 1793228732}]},""" +
                """{"mapFlag": 1, "add_time": 1790866608, "length": 0, "name": "", "bak_maps": [{"mapFlag": 5, "add_time": 1790866608}]}]""",
        ).jsonArray
        assertEquals(
            listOf(
                FloorMap("Hus", 0, savedAt = 1792948453, backupAt = 1793228732),
                FloorMap("Map 2", 1, savedAt = 1790866608, backupAt = 1790866608),
            ),
            parseFloorMapList(mapInfo),
        )
        // A map listed without bak_maps has no backup.
        val noBackup = Json.parseToJsonElement("""[{"mapFlag": 0, "add_time": 1, "name": "Hus"}]""").jsonArray
        assertNull(parseFloorMapList(noBackup).single().backupAt)
    }

    @Test
    fun name_multi_map_matches_capture() {
        // Captured 2026-09-29: name_multi_map [{"length":4,"multi_map":0,"name":"Test"}]
        assertEquals(
            """[{"length":4,"multi_map":0,"name":"Test"}]""",
            encodeNameMultiMapParams(0, "Test").toString(),
        )
    }

    @Test
    fun map_names_are_limited_to_plain_ascii() {
        assertNull(mapNameBlocker("Hus"))
        assertNull(mapNameBlocker("1st floor - East"))
        assertTrue(mapNameBlocker("   ") != null)
        assertTrue(mapNameBlocker("Kælder") != null)
        assertFailsWith<MapEditException> { encodeNameMultiMapParams(0, "Første sal") }
    }

    @Test
    fun load_multi_map_matches_capture() {
        // Captured 2026-09-29: load_multi_map {"data":[1],"need_retry":1}
        assertEquals("""{"data":[1],"need_retry":1}""", encodeLoadMultiMapParams(1).toString())
    }

    @Test
    fun save_new_map_matches_capture() {
        // Captured 2026-09-29: manual_segment_map [{"map_flag":-1}]
        assertEquals("""[{"map_flag":-1}]""", encodeSaveNewMapParams().toString())
    }

    @Test
    fun carpet_flags_are_sent_back_as_read_and_new_carpets_are_rectangles() {
        // Second save of the capture, on map 2: the round carpet re-sent with the flags the robot
        // stored for it, the new rectangle with 0x02338000.
        val round = MapZone(25744, 26784, 26536, 26784, 26536, 25992, 25744, 25992, ZoneKind.CARPET, flags = 0x0633E700L)
        val rect = MapZone(24837, 26196, 25629, 26196, 25629, 25404, 24837, 25404, ZoneKind.CARPET)
        assertEquals(
            """{"flags":[104064768,36929536],"map_index":0,"type":1,"zone_data":""" +
                """[[25744,26784,26536,26784,26536,25992,25744,25992],[24837,26196,25629,26196,25629,25404,24837,25404]]}""",
            encodeCarpetAreaParams(listOf(round, rect), 0, withFlags = true).toString(),
        )
    }

    @Test
    fun loaded_map_comes_from_map_status() {
        // Captured: 7 with map 1 loaded, 11 with map 2, 252-255 while a new map was unsaved.
        assertEquals(1, VacuumStatus(mapStatus = 7).loadedMapFlag)
        assertEquals(2, VacuumStatus(mapStatus = 11).loadedMapFlag)
        assertEquals(UNSAVED_MAP_FLAG, VacuumStatus(mapStatus = 253).loadedMapFlag)
        assertNull(VacuumStatus().loadedMapFlag)
    }

    @Test
    fun unsaved_map_needs_a_map_present() {
        // Captured: 253 with unsave_map_reason 4 after a mapping run; 252 with map_present 0 and no maps.
        assertTrue(VacuumStatus(mapStatus = 253).unsavedMapPresent)
        assertFalse(VacuumStatus(mapStatus = 252).unsavedMapPresent)
        assertFalse(VacuumStatus(mapStatus = 7).unsavedMapPresent)
        assertFalse(VacuumStatus().unsavedMapPresent)
    }

    @Test
    fun set_lab_status_matches_capture() {
        // Captured 2026-09-29: off keeping map 0, then on again.
        assertEquals(
            """{"data":[{"lab_status":1,"reserve_map":0}],"need_retry":1}""",
            encodeSetLabStatusParams(1, reserveMap = 0).toString(),
        )
        assertEquals("""{"data":[{"lab_status":3}],"need_retry":1}""", encodeSetLabStatusParams(3, reserveMap = null).toString())
        assertEquals(1, 3 and LAB_STATUS_MULTI_LEVEL.inv())
        assertEquals(3, 1 or LAB_STATUS_MULTI_LEVEL)
    }

    @Test
    fun set_switch_map_mode_matches_capture() {
        assertEquals("""{"mode":0}""", encodeSwitchMapModeParams(SWITCH_MAP_MODE_SMART).toString())
        assertEquals("""{"mode":1}""", encodeSwitchMapModeParams(SWITCH_MAP_MODE_MANUAL).toString())
    }

    @Test
    fun floor_settings_come_from_status() {
        assertEquals(true, VacuumStatus(labStatus = 3).multiLevelEnabled)
        assertEquals(false, VacuumStatus(labStatus = 1).multiLevelEnabled)
        assertNull(VacuumStatus().multiLevelEnabled)
    }

    @Test
    fun map_saving_is_off_at_lab_status_0() {
        // Issue #17: an S8 that never met the official app reported lab_status 0.
        assertEquals(false, VacuumStatus(labStatus = 0).mapSavingEnabled)
        assertEquals(true, VacuumStatus(labStatus = 1).mapSavingEnabled)
        assertEquals(true, VacuumStatus(labStatus = 3).mapSavingEnabled)
        assertNull(VacuumStatus().mapSavingEnabled)
        // Turning multi-level on from 0 must send the captured 3, not the multi-level bit alone.
        assertEquals(3, 0 or LAB_STATUS_MAP_SAVING or LAB_STATUS_MULTI_LEVEL)
    }

    @Test
    fun plain_forms_match_what_the_s7_applied() {
        // Issue #14, each sent to an S7 on 02.15.78 by the reporter and applied by the robot.
        assertEquals(
            "[[1,14450,31050,25200,31050],[0,14300,31200,25355,31200,25355,30800,14300,30800],[100,0]]",
            saveMapData(
                listOf(MapZone(14300, 31200, 25355, 31200, 25355, 30800, 14300, 30800, ZoneKind.NO_GO)),
                listOf(VirtualWall(14450, 31050, 25200, 31050)),
                mapIndex = 0,
            ).toString(),
        )
        assertEquals("[1,22517,25187,22636,33702]", splitSegmentData(1, 22517, 25187, 22636, 33702).toString())
        assertEquals("[16,17]", mergeSegmentData(16, 17).toString())
        // local_roborock_server issue #148: taken by a Qrevo S5V on 02.33.88.
        assertEquals("""[{"lab_status":1}]""", labStatusData(1, reserveMap = null).toString())
        assertEquals("[1]", loadMultiMapData(1).toString())
    }

    private val ok = V1Response(id = 1, result = Json.parseToJsonElement("""["ok"]"""), error = null)

    private fun refused(code: Int, message: String) =
        V1Response(id = 1, result = null, error = Json.parseToJsonElement("""{"code":$code,"message":"$message"}""").jsonObject)

    /** Sends through [sendDataCommand] and records the params of every attempt. */
    private suspend fun send(
        format: DataParamsFormat,
        sent: MutableList<String>,
        answer: suspend (wrapped: Boolean) -> V1Response,
    ): V1Response = sendDataCommand(format, loadMultiMapData(1)) { params: JsonElement, wrapped ->
        sent += params.toString()
        answer(wrapped)
    }

    @Test
    fun wrapped_form_is_kept_when_the_robot_takes_it() = runTest {
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        assertEquals(ok, send(format, sent) { ok })
        assertEquals(listOf("""{"data":[1],"need_retry":1}"""), sent)
        assertFalse(format.plain)
    }

    @Test
    fun refused_shape_falls_back_to_plain_and_stays_there() = runTest {
        // Issue #14: the S7 answered the wrapped save_map with -10005 "Param is not an array".
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        val answer = send(format, sent) { wrapped -> if (wrapped) refused(-10005, "Param is not an array") else ok }
        assertEquals(ok, answer)
        assertEquals(listOf("""{"data":[1],"need_retry":1}""", "[1]"), sent)
        assertTrue(format.plain)

        sent.clear()
        send(format, sent) { ok }
        assertEquals(listOf("[1]"), sent)
    }

    @Test
    fun silence_falls_back_to_plain() = runTest {
        // Issue #11: the S5 Max never answered the wrapped load_multi_map.
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        val answer = send(format, sent) { wrapped -> if (wrapped) throw RobotSilentException("load_multi_map") else ok }
        assertEquals(ok, answer)
        assertEquals(listOf("""{"data":[1],"need_retry":1}""", "[1]"), sent)
        assertTrue(format.plain)
    }

    @Test
    fun other_refusals_are_not_retried() = runTest {
        // -10006 "Split map failed" is about the line, not the shape: the split logic relies on it.
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        val answer = send(format, sent) { refused(SPLIT_FAILED_CODE, "Split map failed") }
        assertEquals(SPLIT_FAILED_CODE, RobotCommandException("split_segment", answer.error!!).code())
        assertEquals(1, sent.size)
        assertFalse(format.plain)
    }

    @Test
    fun plain_is_not_remembered_when_it_is_refused_too() = runTest {
        // A value the robot rejects in either shape says nothing about the shape it takes.
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        val answer = send(format, sent) { refused(-10005, "Invalid parameters") }
        assertEquals(PARAM_FORMAT_REFUSED_CODE, RobotCommandException("set_lab_status", answer.error!!).code())
        assertEquals(2, sent.size)
        assertFalse(format.plain)
    }

    @Test
    fun a_refusal_in_plain_form_is_reported_and_plain_is_kept() = runTest {
        val format = DataParamsFormat()
        val sent = mutableListOf<String>()
        val answer = send(format, sent) { wrapped ->
            if (wrapped) refused(-10005, "Param is not an array") else refused(SPLIT_FAILED_CODE, "Split map failed")
        }
        assertEquals(SPLIT_FAILED_CODE, RobotCommandException("split_segment", answer.error!!).code())
        assertTrue(format.plain)
    }

    @Test
    fun split_on_a_second_map_matches_capture() {
        // Captured on map 2: split_segment {"data":[1,25000,24050,25000,26450],"need_retry":1},
        // lower-Y end first like the first capture.
        val (a, b) = splitLineAttempts(25000 to 26450, 25000 to 24050).first()
        assertEquals(
            """{"data":[1,25000,24050,25000,26450],"need_retry":1}""",
            encodeSplitSegmentParams(1, a.first, a.second, b.first, b.second).toString(),
        )
    }
}
