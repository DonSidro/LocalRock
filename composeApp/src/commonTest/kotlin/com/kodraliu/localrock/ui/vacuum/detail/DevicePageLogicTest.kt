package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import com.kodraliu.localrock.shared.vacuum.DockErrorCodes
import com.kodraliu.localrock.shared.vacuum.MainTab
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.RouteName
import com.kodraliu.localrock.shared.vacuum.WaterFlow
import com.kodraliu.localrock.shared.vacuum.WaterFlowBand
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes as S
import com.kodraliu.localrock.ui.strings.DeviceStrings as D

class DevicePageLogicTest {

    @Test
    fun start_button_follows_the_job() {
        assertEquals(StartAction.CLEAN, startAction(S.CHARGING, 0))
        assertEquals(StartAction.CLEAN, startAction(S.IDLE, 0))
        assertEquals(StartAction.PAUSE, startAction(S.CLEANING, 1))
        assertEquals(StartAction.PAUSE, startAction(S.MAPPING, 4))
        assertEquals(StartAction.PAUSE, startAction(S.RETURNING_HOME, 1))
        assertEquals(StartAction.CLEAN, startAction(S.RETURNING_HOME, 0))
        assertEquals(StartAction.RESUME, startAction(S.PAUSED, 3))
        assertEquals(StartAction.RESUME, startAction(S.PAUSED, null))
        assertEquals(StartAction.CLEAN, startAction(S.PAUSED, 0))
        // Recharging in the middle of a clean.
        assertEquals(StartAction.RESUME, startAction(S.CHARGING, 1))
    }

    @Test
    fun dock_work_mid_job_keeps_pause_but_only_explains() {
        assertEquals(StartAction.PAUSE, startAction(S.WASHING_MOP, 1))
        assertEquals(StartAction.PAUSE, startAction(S.EMPTYING_BIN, 1))
        assertTrue(pauseWaitsForDock(S.WASHING_MOP, 1))
        assertTrue(isTaskOpen(S.WASHING_MOP, 1))
        // The wash after a finished job is not part of a job.
        assertEquals(StartAction.CLEAN, startAction(S.WASHING_MOP, 0))
        assertFalse(pauseWaitsForDock(S.CLEANING, 1))
    }

    @Test
    fun start_labels_are_the_official_words() {
        assertEquals(D.START_CLEAN, startLabel(StartAction.CLEAN, 0))
        assertEquals(D.START_PAUSE, startLabel(StartAction.PAUSE, 1))
        assertEquals(D.START_RESUME, startLabel(StartAction.RESUME, 1))
        assertEquals(D.MAPPING, startLabel(StartAction.RESUME, 4))
    }

    @Test
    fun start_needs_rooms_on_the_room_tab_and_never_starts_from_routine() {
        assertFalse(startEnabled(StartAction.CLEAN, MainTab.ROOM, 0))
        assertTrue(startEnabled(StartAction.CLEAN, MainTab.ROOM, 2))
        assertFalse(startEnabled(StartAction.CLEAN, MainTab.ROUTINE, 0))
        assertTrue(startEnabled(StartAction.PAUSE, MainTab.ROUTINE, 0))
        assertTrue(startEnabled(StartAction.CLEAN, MainTab.FULL, 0))
        assertTrue(startEnabled(StartAction.CLEAN, MainTab.ZONE, 0))
    }

    @Test
    fun dock_button_opens_the_sheet_on_the_dock_and_returns_elsewhere() {
        assertEquals(DockAction.OPEN_SHEET, dockAction(S.CHARGING))
        assertEquals(DockAction.OPEN_SHEET, dockAction(S.CHARGING_COMPLETE))
        assertEquals(DockAction.OPEN_SHEET, dockAction(S.EMPTYING_BIN))
        assertEquals(DockAction.RETURN, dockAction(S.CLEANING))
        assertEquals(DockAction.RETURN, dockAction(S.IDLE))
        assertEquals(DockAction.STOP_RETURNING, dockAction(S.RETURNING_HOME))
        assertEquals(DockAction.STOP_RETURNING, dockAction(S.GOING_TO_WASH_MOP))
        assertEquals(D.DOCK, dockLabel(S.CHARGING, 0))
        assertEquals(D.RETURN_TO_DOCK, dockLabel(S.IDLE, 0))
        assertEquals(D.BACK_TO_DOCK, dockLabel(S.CLEANING, 1))
        assertEquals(D.STOP_RETURNING, dockLabel(S.RETURNING_HOME, 0))
    }

    @Test
    fun dock_confirms_use_the_official_texts() {
        assertNull(dockConfirm(S.IDLE, 0))
        assertNull(dockConfirm(S.CHARGING, 0))
        assertEquals(D.CONFIRM_END_CLEAN_RETURN, dockConfirm(S.CLEANING, 1))
        assertEquals(D.CONFIRM_END_CLEAN_RETURN, dockConfirm(S.PAUSED, 3))
        assertEquals(D.CONFIRM_STOP_MAPPING_RETURN, dockConfirm(S.MAPPING, 4))
        assertEquals(D.CONFIRM_STOP_CRUISE_RETURN, dockConfirm(S.PATROLLING, 0))
        assertEquals(D.CONFIRM_STOP_TASK_RETURN, dockConfirm(S.GOING_TO_TARGET, 0))
        assertEquals(D.CONFIRM_STOP_RETURN_RESUME, dockConfirm(S.RETURNING_HOME, 1))
        assertNull(dockConfirm(S.RETURNING_HOME, 0))
    }

    @Test
    fun halfway_docking_is_offered_only_mid_clean_on_supporting_robots() {
        val all = listOf(DockOption.MIDWAY_EMPTY, DockOption.MIDWAY_WASH, DockOption.BACK_TO_DOCK)
        assertEquals(all, dockMenuOptions(S.CLEANING, 1, midwayDocking = true, dockType = 29))
        assertEquals(all, dockMenuOptions(S.PAUSED, 1, midwayDocking = true, dockType = 29))
        assertTrue(dockMenuOptions(S.CLEANING, 1, midwayDocking = false, dockType = 29).isEmpty())
        assertTrue(dockMenuOptions(S.CLEANING, 1, midwayDocking = true, dockType = 0).isEmpty())
        assertTrue(dockMenuOptions(S.IDLE, 0, midwayDocking = true, dockType = 29).isEmpty())
        assertTrue(dockMenuOptions(S.GOING_TO_WASH_MOP, 1, midwayDocking = true, dockType = 29).isEmpty())
        assertEquals(D.MIDWAY_EMPTY, DockOption.MIDWAY_EMPTY.label)
        assertEquals(D.MIDWAY_WASH, DockOption.MIDWAY_WASH.label)
    }

    @Test
    fun status_words_follow_the_official_table() {
        assertEquals(D.GETTING_INFO, officialStatusText(VacuumStatus()))
        assertEquals(D.STATUS_CHARGED, officialStatusText(VacuumStatus(state = S.CHARGING, battery = 100)))
        assertEquals(D.STATUS_CHARGING, officialStatusText(VacuumStatus(state = S.CHARGING, battery = 80)))
        assertEquals(D.STATUS_READY, officialStatusText(VacuumStatus(state = S.IDLE)))
        assertEquals(D.STATUS_RETURNING, officialStatusText(VacuumStatus(state = S.DOCKING)))
        assertEquals(D.STATUS_OFFLINE, officialStatusText(VacuumStatus(state = S.OFFLINE)))
        assertEquals(D.STATUS_VACUUMING, officialStatusText(VacuumStatus(state = S.CLEANING, waterBoxCustomMode = WaterBoxMode.OFF)))
        assertEquals(D.STATUS_MOPPING, officialStatusText(VacuumStatus(state = S.SEGMENT_CLEANING, fanPower = VacuumFanPower.OFF, waterBoxCustomMode = WaterBoxMode.LOW)))
        assertEquals(D.STATUS_VAC_AND_MOPPING, officialStatusText(VacuumStatus(state = S.ZONED_CLEANING, fanPower = 102, waterBoxCustomMode = 235)))
        assertEquals(D.STATUS_SEARCHING_PETS, officialStatusText(VacuumStatus(state = S.PATROLLING, commonStatus = 2 shl 3)))
        assertEquals(D.STATUS_ON_CRUISE, officialStatusText(VacuumStatus(state = S.PATROLLING, commonStatus = 0)))
    }

    @Test
    fun state_26_is_worded_by_back_type() {
        assertEquals(D.STATUS_DOCKING_TO_WASH, officialStatusText(VacuumStatus(state = S.GOING_TO_WASH_MOP, backType = 1)))
        assertEquals(D.STATUS_DOCKING_TO_SETUP_MOP, backDockText(2))
        assertEquals(D.STATUS_DOCKING_TO_DETACH_MOP, backDockText(3))
        assertEquals(D.STATUS_DOCKING_TO_EMPTY, officialStatusText(VacuumStatus(state = S.GOING_TO_WASH_MOP, backType = 4)))
        assertEquals(D.STATUS_GOING_TO_WASH, backDockText(null))
        assertEquals(D.STATUS_GOING_TO_WASH, backDockText(-1))
    }

    @Test
    fun task_title_replaces_the_name_while_a_task_runs() {
        assertEquals(D.STATUS_FULL_CLEANING, taskTitle(VacuumStatus(state = S.CLEANING)))
        assertEquals(D.STATUS_ROOM_CLEANING, taskTitle(VacuumStatus(state = S.PAUSED, inCleaning = 3)))
        assertEquals(D.STATUS_MAPPING_TASK, taskTitle(VacuumStatus(state = S.MAPPING)))
        assertNull(taskTitle(VacuumStatus(state = S.CHARGING)))
        assertNull(taskTitle(VacuumStatus(state = S.PAUSED, inCleaning = 0)))
    }

    @Test
    fun drying_and_tip_texts_fill_their_numbers() {
        assertEquals("Drying the mop, estimated finish after: 2 minute(s)", dryingText(61))
        assertEquals(D.DRYING, dryingText(0))
        assertEquals(D.TIP_SELECT_ROOMS, roomTip(0))
        assertEquals("3 cleaning areas selected", roomTip(3))
    }

    @Test
    fun remembered_tab_falls_back_to_full_when_not_offered() {
        val three = listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE)
        assertEquals(MainTab.ZONE, resolveTab(MainTab.ZONE, three))
        assertEquals(MainTab.FULL, resolveTab(MainTab.ROUTINE, three))
        assertEquals(listOf(D.TAB_FULL, D.TAB_ROOM, D.TAB_ZONE, D.TAB_ROUTINE), MainTab.entries.map { tabLabel(it) })
    }

    @Test
    fun mode_chip_follows_the_current_mode() {
        assertEquals(D.MODE_SMARTPLAN, modeChipLabel(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART, 0))
        assertEquals(D.MODE_CUSTOMIZE, modeChipLabel(VacuumFanPower.CUSTOM, WaterBoxMode.CUSTOM, MopRoute.CUSTOM, 0))
        assertEquals(D.MODE_VAC_THEN_MOP, modeChipLabel(102, 235, 300, 1))
        assertEquals(D.MODE_MOP, modeChipLabel(VacuumFanPower.OFF, WaterBoxMode.LOW, 300, 0))
        assertEquals(D.MODE_VACUUM, modeChipLabel(102, WaterBoxMode.OFF, 300, null))
        assertEquals(D.MODE_VAC_AND_MOP, modeChipLabel(102, 235, 300, 0))
    }

    @Test
    fun general_sub_tabs_match_the_robot() {
        // Curv: clean-then-mop, no pure mop (screenshot bae73cca).
        assertEquals(listOf(GeneralMode.VAC_THEN_MOP, GeneralMode.VAC_AND_MOP, GeneralMode.VACUUM), generalModes(true, false, false))
        assertEquals(listOf(GeneralMode.VAC_AND_MOP, GeneralMode.MOP, GeneralMode.VACUUM), generalModes(false, true, false))
        assertTrue(GeneralMode.MOP in generalModes(true, false, currentlyMop = true))
        assertEquals(GeneralMode.VAC_THEN_MOP, currentGeneralMode(102, 235, 1))
        assertEquals(GeneralMode.VACUUM, currentGeneralMode(102, WaterBoxMode.OFF, 0))
        assertEquals(GeneralMode.MOP, currentGeneralMode(VacuumFanPower.OFF, 202, 0))
        assertEquals(D.TIP_VAC_AND_MOP, generalModeTip(GeneralMode.VAC_AND_MOP))
        assertEquals(D.MODE_VAC_THEN_MOP, generalModeLabel(GeneralMode.VAC_THEN_MOP))
    }

    @Test
    fun switching_general_mode_only_changes_what_must_change() {
        assertEquals(MotorChange(null, null), motorChangeFor(GeneralMode.VAC_AND_MOP, 102, 202))
        assertEquals(MotorChange(VacuumFanPower.BALANCED, WaterBoxMode.LOW), motorChangeFor(GeneralMode.VAC_THEN_MOP, VacuumFanPower.OFF, WaterBoxMode.OFF))
        assertEquals(MotorChange(VacuumFanPower.OFF, null), motorChangeFor(GeneralMode.MOP, 102, 202))
        assertEquals(MotorChange(null, WaterBoxMode.OFF), motorChangeFor(GeneralMode.VACUUM, 102, 202))
    }

    @Test
    fun current_plan_reads_the_three_codes() {
        assertEquals(CleanPlan.SMART, currentPlan(VacuumFanPower.SMART, WaterBoxMode.SMART, MopRoute.SMART))
        assertEquals(CleanPlan.PER_ROOM, currentPlan(106, 204, 302))
        assertEquals(CleanPlan.MANUAL, currentPlan(102, 235, 300))
    }

    @Test
    fun dock_rows_name_the_problem_or_say_normal() {
        assertEquals(D.UNKNOWN to DockRowLevel.UNKNOWN, dockRow(null, CLEAN_TANK_PROBLEMS))
        assertEquals(D.NORMAL to DockRowLevel.OK, dockRow(0, CLEAN_TANK_PROBLEMS))
        assertEquals(D.OUT_OF_WATER to DockRowLevel.WARN, dockRow(DockErrorCodes.WATER_EMPTY, CLEAN_TANK_PROBLEMS))
        assertEquals(D.TANK_FULL to DockRowLevel.WARN, dockRow(DockErrorCodes.DIRTY_TANK_FULL, DIRTY_TANK_PROBLEMS))
        assertEquals(D.NORMAL to DockRowLevel.OK, dockRow(DockErrorCodes.DIRTY_TANK_FULL, DUST_BAG_PROBLEMS))
    }

    @Test
    fun status_card_line_shows_area_and_minutes() {
        assertEquals(D.NO_CLEAN_YET, cleanDetailLine(null, null, false))
        assertEquals("12.3 m² last clean · 25 min", cleanDetailLine(12_300_000, 1500, false))
        assertEquals("1.0 m² so far", cleanDetailLine(1_000_000, 0, true))
    }

    @Test
    fun water_flow_band_words_follow_the_slider() {
        assertEquals(D.WATER_FLOW_SLIGHT, waterFlowBandLabel(WaterFlow.band(1)))
        assertEquals(D.WATER_FLOW_MEDIUM, waterFlowBandLabel(WaterFlow.band(15)))
        assertEquals(D.WATER_FLOW_EXTREME, waterFlowBandLabel(WaterFlow.band(30)))
        assertEquals(7, WaterFlowBand.entries.map { waterFlowBandLabel(it) }.toSet().size)
    }

    @Test
    fun water_label_names_slide_codes_and_chip_codes() {
        assertEquals("${D.WATER_FLOW_MEDIUM} 15", waterLabel(235))
        assertEquals("${D.WATER_FLOW_GENTLE} 8", waterLabel(228))
        assertEquals(D.WATER_LOW, waterLabel(WaterBoxMode.LOW))
        assertEquals(D.WATER_OFF, waterLabel(WaterBoxMode.OFF))
        assertNull(waterLabel(WaterBoxMode.SMART))
    }

    @Test
    fun route_words() {
        assertEquals(D.ROUTE_FAST, routeLabel(RouteName.FAST))
        assertEquals(D.ROUTE_DEEP_PLUS, routeLabel(RouteName.DEEP_PLUS))
    }

    @Test
    fun vac_then_mop_keeps_only_the_routes_the_robot_takes() {
        assertEquals(MopRoute.STANDARD, routeForVacThenMop(MopRoute.DEEP, cleanEfficiency = false))
        assertEquals(MopRoute.STANDARD, routeForVacThenMop(MopRoute.DEEP_PLUS, cleanEfficiency = false))
        assertEquals(MopRoute.FAST, routeForVacThenMop(MopRoute.FAST, cleanEfficiency = false))
        assertEquals(MopRoute.DEEP, routeForVacThenMop(MopRoute.DEEP, cleanEfficiency = true))
    }

    @Test
    fun custom_water_label_names_the_band_when_the_amount_is_known() {
        assertEquals(D.WATER_CUSTOM, customWaterLabel(null, 0))
        assertEquals("${D.WATER_CUSTOM} | ${waterFlowBandLabel(WaterFlowBand.MODERATE)}", customWaterLabel(100, 0))
    }
}
