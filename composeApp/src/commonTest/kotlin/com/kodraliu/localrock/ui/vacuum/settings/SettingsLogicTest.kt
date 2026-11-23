package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.shared.vacuum.BetaFeatureDef
import com.kodraliu.localrock.shared.vacuum.BetaFeatureDefs
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsLogicTest {
    // 2026-11-13 was a Friday (weekday 5 in CleanTimer's 0 = Sunday numbering).
    private val friday1000 = LocalDateTime(2026, 11, 13, 10, 0)

    private fun timer(id: String, h: Int, m: Int, days: Set<Int> = emptySet(), enabled: Boolean = true) =
        CleanTimer(id, enabled, h, m, days)

    @Test
    fun the_next_cleaning_is_the_earliest_enabled_timer_still_ahead_today() {
        val timers = listOf(
            timer("past", 9, 0),
            timer("off", 11, 0, enabled = false),
            timer("monday", 10, 30, setOf(1)),
            timer("friday", 14, 15, setOf(5)),
            timer("daily", 12, 5),
        )
        assertEquals(12 to 5, nextCleaningToday(timers, friday1000))
        assertEquals("The next cleaning will start at 12:05 today.", schedulesDetail(12 to 5))
    }

    @Test
    fun no_schedule_ahead_today_shows_the_plain_detail() {
        assertNull(nextCleaningToday(listOf(timer("past", 9, 0)), friday1000))
        assertEquals(SettingsStrings.SCHEDULES_DETAIL, schedulesDetail(null))
    }

    @Test
    fun maintenance_reads_normal_until_a_supply_runs_out() {
        assertEquals("Normal", maintenanceDetail(null))
        assertEquals("Normal", maintenanceDetail(ConsumableStatus(mainBrushWorkTime = 3600)))
        assertTrue(suppliesDue(ConsumableStatus(sensorDirtyTime = ConsumableStatus.SENSOR_LIFETIME_S)))
        assertTrue(suppliesDue(ConsumableStatus(strainerWorkTime = 200)))
        assertEquals(SettingsStrings.MAINTENANCE_DUE, maintenanceDetail(ConsumableStatus(filterWorkTime = ConsumableStatus.FILTER_LIFETIME_S + 1)))
    }

    @Test
    fun the_header_card_shows_the_newest_run_in_minutes_and_square_metres() {
        val older = CleanRecord(1000, 2000, 600, 10_000_000, 0, 1)
        val newer = CleanRecord(5000, 6000, 1801, 92_852_500, 0, 1, cleanedAreaMm2 = 122_650_000)
        assertEquals(newer, lastCleanRecord(listOf(older, newer)))
        assertEquals("31 min" to "122.6 m²", lastCleanFigures(newer))
        assertEquals("10 min" to "10 m²", lastCleanFigures(older))
        assertEquals("--" to "--", lastCleanFigures(null))
    }

    @Test
    fun carpet_cards_use_the_official_labels_and_wire_values() {
        val cards = carpetModeCards(1)
        assertEquals(listOf(1, 0, 2), cards.map { it.value })
        assertEquals(listOf("Vacuum Only", "Avoid Carpet", "Vac & Mop"), cards.map { it.title })
        assertEquals(3, carpetModeCards(3).last().value)
        assertEquals("Dynamic Lift", carpetModeCards(3).last().title)
    }

    @Test
    fun wash_mode_cards_carry_the_official_descriptions() {
        val cards = washModeCards(listOf(10 to "Smart", 0 to "Light", 1 to "Balanced", 2 to "Deep", 8 to "Soak"))
        assertEquals(
            listOf(SettingsStrings.WASH_SMART_DETAIL, SettingsStrings.WASH_LIGHT_DETAIL, SettingsStrings.WASH_BALANCED_DETAIL,
                SettingsStrings.WASH_DEEP_DETAIL, SettingsStrings.WASH_SOAK_DETAIL),
            cards.map { it.detail },
        )
        assertNull(washModeCards(listOf(8 to "Super Deep")).single().detail)
    }

    @Test
    fun wash_temperature_cards_follow_the_dock_type() {
        val curv = washTemperatureCards(29)
        assertEquals(listOf(0, 2), curv.map { it.value })
        assertEquals(listOf(SettingsStrings.WASH_ROOM_TEMPERATURE_DETAIL, SettingsStrings.WASH_HIGH_TEMPERATURE_DETAIL), curv.map { it.detail })
        assertEquals(listOf("Room Temperature", "Warm Water", "Hot Water"), washTemperatureCards(17).map { it.title })
    }

    @Test
    fun dry_time_cards_describe_the_named_options_only() {
        val cards = dryTimeCards(DockCapabilities("roborock.vacuum.a245", null, null).namedDryTimeOptions(3600))
        assertEquals(listOf("Eco", "Standard", "Deep", "1 h"), cards.map { it.title })
        assertEquals(
            listOf(SettingsStrings.DRY_ECO_DETAIL, SettingsStrings.DRY_STANDARD_DETAIL, SettingsStrings.DRY_DEEP_DETAIL, null),
            cards.map { it.detail },
        )
    }

    @Test
    fun the_cross_card_is_third_when_the_robot_supports_it() {
        val cards = carpetModeCards(1, crossSupported = true)
        assertEquals(listOf(1, 0, CARPET_CROSS_CARD, 2), cards.map { it.value })
        assertEquals(listOf("Vacuum Only", "Avoid Carpet", "Cross", "Vac & Mop"), cards.map { it.title })
    }

    @Test
    fun cross_is_selected_whenever_its_bit_is_on() {
        assertEquals(CARPET_CROSS_CARD, selectedCarpetCard(1, crossOn = true))
        assertEquals(CARPET_CROSS_CARD, selectedCarpetCard(0, crossOn = true))
        assertEquals(2, selectedCarpetCard(2, crossOn = false))
        assertNull(selectedCarpetCard(null, crossOn = false))
    }

    @Test
    fun cross_sends_only_cross_on_and_other_cards_set_the_mode_then_turn_cross_off() {
        assertEquals(listOf(CarpetWrite.Cross(true)), carpetCardWrites(CARPET_CROSS_CARD, crossSupported = true))
        assertEquals(listOf(CarpetWrite.CleanMode(0), CarpetWrite.Cross(false)), carpetCardWrites(0, crossSupported = true))
        assertEquals(listOf(CarpetWrite.CleanMode(2)), carpetCardWrites(2, crossSupported = false))
    }

    private fun state(id: String) = BetaFeatureState(BetaFeatureDefs.first { it.id == id }, value = 1)

    @Test
    fun floor_rows_fall_into_the_official_groups_in_order() {
        val rows = listOf("pet_supplies_deep_clean", "gap_deep_clean", "cleaning_pattern", "right_brush_stretch", "corner_mop_stretch", "climbing_arms_high_sill")
            .map(::state)
        val groups = floorFeatureGroups(rows).map { g -> g.map { it.def.id } }
        assertEquals(
            listOf(
                listOf("cleaning_pattern"),
                listOf("right_brush_stretch", "corner_mop_stretch", "gap_deep_clean"),
                listOf("pet_supplies_deep_clean"),
                listOf("climbing_arms_high_sill"),
            ),
            groups,
        )
    }

    @Test
    fun obstacle_rows_fall_into_the_official_groups_in_order() {
        val rows = listOf("fill_light", "pet_snapshot", "less_collision_mode", "ai_environment", "obstacle_photos", "pet_mode", "obstacle_avoidance", "follow_low_obstacles")
            .map(::state)
        val groups = obstacleFeatureGroups(rows).map { g -> g.map { it.def.id } }
        assertEquals(
            listOf(
                listOf("obstacle_avoidance", "obstacle_photos"),
                listOf("less_collision_mode"),
                listOf("pet_mode", "pet_snapshot"),
                listOf("ai_environment"),
                listOf("fill_light"),
                listOf("follow_low_obstacles"),
            ),
            groups,
        )
        assertTrue(obstacleFeatureGroups(listOf(state("identify_room"))).isEmpty())
    }

    @Test
    fun floor_groups_ignore_rows_from_other_sections() {
        assertTrue(floorFeatureGroups(listOf(state("avoid_collision"), state("optimize_battery"))).isEmpty())
    }

    @Test
    fun dock_rows_join_the_official_groups() {
        assertEquals(DockGroup.WASH, dockGroupOf(BetaFeatureDefs.first { it.id == "clean_fluid_delivery" }))
        assertEquals(DockGroup.EMPTY, dockGroupOf(BetaFeatureDefs.first { it.id == "dust_collection_switch" }))
        assertEquals(DockGroup.OTHER, dockGroupOf(BetaFeatureDefs.first { it.id == "avoid_collision" }))
    }

    @Test
    fun robot_rows_split_between_more_options_and_robot_voice() {
        val rows = listOf("water_leak_check", "voice_control_led", "optimize_battery", "full_duplex", "avoid_collision").map(::state)
        assertEquals(listOf("voice_control_led"), rows.robotRows(RobotRowPlace.LIGHTS).map { it.def.id })
        assertEquals(listOf("optimize_battery"), rows.robotRows(RobotRowPlace.ADJUSTED_BATTERY).map { it.def.id })
        assertEquals(listOf("full_duplex"), rows.robotRows(RobotRowPlace.VOICE).map { it.def.id })
        assertEquals(listOf("water_leak_check"), rows.robotRows(RobotRowPlace.OTHER).map { it.def.id })
        assertFalse(rows.robotRows(RobotRowPlace.OTHER).any { it.def.id == "avoid_collision" })
    }

    @Test
    fun voice_assistant_switch_sits_on_the_voice_page_not_more_options() {
        // Built here because the voice_assistant definition lives on the advertised-features branch.
        val def = BetaFeatureDef("voice_assistant", "Voice Assistant", "", 37, "get_voice_service_switch", "set_voice_service_switch", FeatureSection.ROBOT)
        val rows = listOf(BetaFeatureState(def, value = 1), state("water_leak_check"))
        assertEquals(listOf("voice_assistant"), rows.robotRows(RobotRowPlace.VOICE_SWITCH).map { it.def.id })
        assertEquals(listOf("water_leak_check"), rows.robotRows(RobotRowPlace.OTHER).map { it.def.id })
    }

    @Test
    fun clock_text_pads_hours_and_minutes() {
        assertEquals("07:05", clockText(7, 5))
    }

    @Test
    fun robot_voice_row_shows_the_volume_level() {
        assertEquals("Volume 75%", robotVoiceDetail(75))
        assertEquals("Volume 100%", robotVoiceDetail(130))
        assertEquals("Volume", robotVoiceDetail(null))
    }

    @Test
    fun dock_page_hides_the_auto_drying_switch_behind_drying_start_method() {
        val withMethod = dockPageRows(listOf(state("drying_start_method")), mopDrying = true, dss = 168)
        assertEquals("drying_start_method", withMethod.dryingStartMethod?.def?.id)
        assertFalse(withMethod.autoDryingSwitch)
        val without = dockPageRows(emptyList(), mopDrying = true, dss = 168)
        assertTrue(without.autoDryingSwitch)
        assertFalse(dockPageRows(emptyList(), mopDrying = null, dss = 168).autoDryingSwitch)
    }

    @Test
    fun dock_page_shows_cleaning_fluid_only_when_the_dock_reports_fluid() {
        val rows = listOf(state("clean_fluid_delivery"), state("dust_collection_interval"))
        assertEquals(emptyList(), dockPageRows(rows, null, dss = 168).wash.map { it.def.id })
        assertEquals(listOf("clean_fluid_delivery"), dockPageRows(rows, null, dss = 168 or (1 shl 10)).wash.map { it.def.id })
        assertEquals(listOf("dust_collection_interval"), dockPageRows(rows, null, dss = 168).empty.map { it.def.id })
    }

    @Test
    fun dock_page_ignores_rows_from_other_sections() {
        val rows = dockPageRows(listOf(state("avoid_collision")), null, null)
        assertTrue(rows.other.isEmpty() && rows.wash.isEmpty() && rows.empty.isEmpty())
    }

    @Test
    fun tray_self_clean_asks_to_end_a_running_task_only() {
        assertTrue(traySelfCleanEndsTask(5))
        assertTrue(traySelfCleanEndsTask(18))
        assertFalse(traySelfCleanEndsTask(8))
        assertFalse(traySelfCleanEndsTask(null))
    }
}
