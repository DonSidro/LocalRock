package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.vacuum.*

import com.kodraliu.localrock.shared.vacuum.MainTab
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.RouteName
import com.kodraliu.localrock.shared.vacuum.WaterFlow
import com.kodraliu.localrock.shared.vacuum.WaterFlowBand
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.VacuumStatus
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode
import com.kodraliu.localrock.shared.vacuum.isCustomizeModeSet
import com.kodraliu.localrock.shared.vacuum.isSmartModeSet
import com.kodraliu.localrock.ui.strings.DeviceStrings
import com.kodraliu.localrock.ui.strings.DeviceStrings.fill

// Pure rules behind the device page: button states, labels, confirms and status words (plugin notes in 1-device-page-navigation.md).

internal val FAN_OPTIONS = listOf(
    VacuumFanPower.QUIET to DeviceStrings.FAN_QUIET,
    VacuumFanPower.BALANCED to DeviceStrings.FAN_BALANCED,
    VacuumFanPower.TURBO to DeviceStrings.FAN_TURBO,
    VacuumFanPower.MAX to DeviceStrings.FAN_MAX,
)

/** A room can also go without suction, the official app's mop-only setting. */
internal val ROOM_FAN_OPTIONS = listOf(VacuumFanPower.OFF to DeviceStrings.FAN_OFF) + FAN_OPTIONS

internal val WATER_OPTIONS = listOf(
    WaterBoxMode.OFF to DeviceStrings.WATER_OFF,
    WaterBoxMode.LOW to DeviceStrings.WATER_LOW,
    WaterBoxMode.MEDIUM to DeviceStrings.WATER_MEDIUM,
    WaterBoxMode.HIGH to DeviceStrings.WATER_HIGH,
)

internal val DEEP_ROUTES = setOf(MopRoute.DEEP, MopRoute.DEEP_PLUS)

/** Slider position when the robot reports no slide level (off, smart or custom). */
internal const val DEFAULT_WATER_FLOW_LEVEL = 15

internal fun waterFlowBandLabel(band: WaterFlowBand): String = when (band) {
    WaterFlowBand.SLIGHT -> DeviceStrings.WATER_FLOW_SLIGHT
    WaterFlowBand.LOW -> DeviceStrings.WATER_FLOW_LOW
    WaterFlowBand.GENTLE -> DeviceStrings.WATER_FLOW_GENTLE
    WaterFlowBand.MEDIUM -> DeviceStrings.WATER_FLOW_MEDIUM
    WaterFlowBand.MODERATE -> DeviceStrings.WATER_FLOW_MODERATE
    WaterFlowBand.HIGH -> DeviceStrings.WATER_FLOW_HIGH
    WaterFlowBand.EXTREME -> DeviceStrings.WATER_FLOW_EXTREME
}

internal fun routeLabel(name: RouteName): String = when (name) {
    RouteName.FAST -> DeviceStrings.ROUTE_FAST
    RouteName.STANDARD -> DeviceStrings.ROUTE_STANDARD
    RouteName.DEEP -> DeviceStrings.ROUTE_DEEP
    RouteName.DEEP_PLUS -> DeviceStrings.ROUTE_DEEP_PLUS
}

/** The water word for a code: the band on slide codes, else the chip label, else null. */
internal fun waterLabel(code: Int?): String? =
    if (code != null && code in WaterFlow.codeFor(WaterFlow.MIN_LEVEL)..WaterFlow.codeFor(WaterFlow.MAX_LEVEL)) {
        val level = WaterFlow.levelOf(code) ?: DEFAULT_WATER_FLOW_LEVEL
        "${waterFlowBandLabel(WaterFlow.band(level))} $level"
    } else {
        WATER_OPTIONS.firstOrNull { it.first == code }?.second
    }

internal val ACTIVE_CLEANING_STATES = setOf(
    VacuumStateCodes.CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SPOT_CLEANING,
)

private val RETURNING_STATES = setOf(VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.DOCKING, VacuumStateCodes.GOING_TO_WASH_MOP)

private val ON_DOCK_STATES = setOf(
    VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE, VacuumStateCodes.CHARGING_ERROR,
    VacuumStateCodes.UPDATING, VacuumStateCodes.EMPTYING_BIN, VacuumStateCodes.WASHING_MOP,
    VacuumStateCodes.WASHING_MOP_ALT, VacuumStateCodes.DRYING_MOP,
)

// Tasks the round button pauses, besides cleaning (official canPauseTask).
private val PAUSABLE_TASK_STATES = setOf(
    VacuumStateCodes.MAPPING, VacuumStateCodes.PATROLLING, VacuumStateCodes.GOING_TO_TARGET,
)

// States in which an open job (in_cleaning != 0) can be resumed (official isReadyForCleanTaskResume).
private val RESUMABLE_STATES = setOf(
    VacuumStateCodes.PAUSED, VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE,
    VacuumStateCodes.CHARGER_DISCONNECTED, VacuumStateCodes.ERROR,
)

// Dock work partway through a job: the official button still shows Pause but only explains (canPauseByToast).
private val DOCK_WORK_STATES = setOf(VacuumStateCodes.WASHING_MOP, VacuumStateCodes.WASHING_MOP_ALT, VacuumStateCodes.EMPTYING_BIN)

/** in_cleaning 4 is a paused quick mapping run. */
private const val IN_CLEANING_MAPPING = 4

internal enum class StartAction { CLEAN, PAUSE, RESUME }

/** True while the dock works partway through a job; Pause then explains instead of sending app_pause. */
internal fun pauseWaitsForDock(state: Int?, inCleaning: Int?): Boolean =
    state in DOCK_WORK_STATES && (inCleaning ?: 0) != 0

internal fun startAction(state: Int?, inCleaning: Int?): StartAction {
    val jobOpen = (inCleaning ?: 0) != 0
    return when {
        state in ACTIVE_CLEANING_STATES || state in PAUSABLE_TASK_STATES -> StartAction.PAUSE
        pauseWaitsForDock(state, inCleaning) -> StartAction.PAUSE
        state in RETURNING_STATES && jobOpen -> StartAction.PAUSE
        // An unknown in_cleaning on a paused robot keeps the old "Resume" behaviour.
        state == VacuumStateCodes.PAUSED && inCleaning == null -> StartAction.RESUME
        state in RESUMABLE_STATES && jobOpen -> StartAction.RESUME
        else -> StartAction.CLEAN
    }
}

internal fun startLabel(action: StartAction, inCleaning: Int?): String = when (action) {
    StartAction.CLEAN -> DeviceStrings.START_CLEAN
    StartAction.PAUSE -> DeviceStrings.START_PAUSE
    StartAction.RESUME -> if (inCleaning == IN_CLEANING_MAPPING) DeviceStrings.MAPPING else DeviceStrings.START_RESUME
}

/** True while a job is open: the Stop control shows and mode changes ask first. */
internal fun isTaskOpen(state: Int?, inCleaning: Int?): Boolean = startAction(state, inCleaning) != StartAction.CLEAN

internal enum class DockAction { OPEN_SHEET, RETURN, STOP_RETURNING }

internal fun dockAction(state: Int?): DockAction = when (state) {
    in RETURNING_STATES -> DockAction.STOP_RETURNING
    null, 0, VacuumStateCodes.OFFLINE, in ON_DOCK_STATES -> DockAction.OPEN_SHEET
    else -> DockAction.RETURN
}

internal fun dockLabel(state: Int?, inCleaning: Int?): String = when (dockAction(state)) {
    DockAction.OPEN_SHEET -> DeviceStrings.DOCK
    DockAction.STOP_RETURNING -> DeviceStrings.STOP_RETURNING
    DockAction.RETURN -> if (isTaskOpen(state, inCleaning)) DeviceStrings.BACK_TO_DOCK else DeviceStrings.RETURN_TO_DOCK
}

/** The official confirm before the Dock button interrupts something; null when it acts straight away. */
internal fun dockConfirm(state: Int?, inCleaning: Int?): String? {
    val jobOpen = (inCleaning ?: 0) != 0
    return when (dockAction(state)) {
        DockAction.OPEN_SHEET -> null
        DockAction.STOP_RETURNING -> if (jobOpen) DeviceStrings.CONFIRM_STOP_RETURN_RESUME else null
        DockAction.RETURN -> when {
            state == VacuumStateCodes.MAPPING -> DeviceStrings.CONFIRM_STOP_MAPPING_RETURN
            state == VacuumStateCodes.PATROLLING -> DeviceStrings.CONFIRM_STOP_CRUISE_RETURN
            state in ACTIVE_CLEANING_STATES || (state == VacuumStateCodes.PAUSED && inCleaning != 0) -> DeviceStrings.CONFIRM_END_CLEAN_RETURN
            isTaskOpen(state, inCleaning) -> DeviceStrings.CONFIRM_STOP_TASK_RETURN
            else -> null
        }
    }
}

/** The sub-title status word: the official state names, with cleaning refined by what the robot is doing. */
internal fun officialStatusText(status: VacuumStatus): String = when (val state = status.state) {
    null, 0 -> DeviceStrings.GETTING_INFO
    VacuumStateCodes.CHARGING -> if (status.battery == 100) DeviceStrings.STATUS_CHARGED else DeviceStrings.STATUS_CHARGING
    VacuumStateCodes.CHARGING_COMPLETE -> DeviceStrings.STATUS_CHARGED
    VacuumStateCodes.CHARGING_ERROR -> DeviceStrings.STATUS_CHARGING_ERROR
    VacuumStateCodes.STARTING -> DeviceStrings.STATUS_CLEANING
    VacuumStateCodes.CHARGER_DISCONNECTED -> DeviceStrings.STATUS_SLEEPING
    VacuumStateCodes.IDLE -> DeviceStrings.STATUS_READY
    VacuumStateCodes.PAUSED -> DeviceStrings.STATUS_PAUSED
    VacuumStateCodes.SPOT_CLEANING -> DeviceStrings.STATUS_SPOT_CLEAN
    VacuumStateCodes.ERROR -> DeviceStrings.STATUS_ERROR
    VacuumStateCodes.UPDATING -> DeviceStrings.STATUS_UPDATING
    VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.DOCKING -> DeviceStrings.STATUS_RETURNING
    VacuumStateCodes.REMOTE_CONTROL_ACTIVE, VacuumStateCodes.MANUAL_MODE -> DeviceStrings.STATUS_REMOTE
    VacuumStateCodes.GOING_TO_TARGET -> DeviceStrings.STATUS_GOING_TO_TARGET
    VacuumStateCodes.CLEANING, VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SEGMENT_CLEANING -> when {
        status.waterBoxCustomMode == WaterBoxMode.OFF -> DeviceStrings.STATUS_VACUUMING
        status.fanPower == VacuumFanPower.OFF -> DeviceStrings.STATUS_MOPPING
        else -> DeviceStrings.STATUS_VAC_AND_MOPPING
    }
    VacuumStateCodes.EMPTYING_BIN -> DeviceStrings.STATUS_EMPTYING
    VacuumStateCodes.WASHING_MOP, VacuumStateCodes.WASHING_MOP_ALT -> DeviceStrings.STATUS_WASHING_MOP
    VacuumStateCodes.GOING_TO_WASH_MOP -> backDockText(status.backType)
    VacuumStateCodes.IN_CALL -> DeviceStrings.STATUS_IN_CALL
    VacuumStateCodes.MAPPING -> DeviceStrings.STATUS_MAPPING
    VacuumStateCodes.EGG_ATTACK -> DeviceStrings.STATUS_SCANNING
    VacuumStateCodes.PATROLLING -> if (status.isPetPatrol) DeviceStrings.STATUS_SEARCHING_PETS else DeviceStrings.STATUS_ON_CRUISE
    VacuumStateCodes.SETUP_MOP -> DeviceStrings.STATUS_REINSTALLING_MOP
    VacuumStateCodes.TEARDOWN_MOP -> DeviceStrings.STATUS_DETACHING_MOP
    38 -> DeviceStrings.STATUS_SORTING
    VacuumStateCodes.OFFLINE -> DeviceStrings.STATUS_OFFLINE
    VacuumStateCodes.LOCKED -> DeviceStrings.STATUS_SAVING_MAP
    else -> VacuumStateCodes.describe(state)
}

/** The task name the official nav bar shows instead of the device name while a task runs; null otherwise. */
internal fun taskTitle(status: VacuumStatus): String? = when (status.state) {
    VacuumStateCodes.CLEANING -> DeviceStrings.STATUS_FULL_CLEANING
    VacuumStateCodes.SEGMENT_CLEANING -> DeviceStrings.STATUS_ROOM_CLEANING
    VacuumStateCodes.ZONED_CLEANING -> DeviceStrings.STATUS_ZONE_CLEANING
    VacuumStateCodes.SPOT_CLEANING -> DeviceStrings.STATUS_SPOT_CLEAN
    VacuumStateCodes.MAPPING -> DeviceStrings.STATUS_MAPPING_TASK
    VacuumStateCodes.PATROLLING -> DeviceStrings.STATUS_ON_CRUISE
    VacuumStateCodes.GOING_TO_TARGET -> DeviceStrings.STATUS_GOING_TO_TARGET
    VacuumStateCodes.PAUSED -> when (status.inCleaning) {
        1 -> DeviceStrings.STATUS_FULL_CLEANING
        2 -> DeviceStrings.STATUS_ZONE_CLEANING
        3 -> DeviceStrings.STATUS_ROOM_CLEANING
        IN_CLEANING_MAPPING -> DeviceStrings.STATUS_MAPPING_TASK
        else -> null
    }
    else -> null
}

/** "Drying the mop, estimated finish after: N minute(s)", or just "Drying" without a time. */
internal fun dryingText(remainingDrySec: Long?): String =
    remainingDrySec?.takeIf { it > 0 }?.let { fill(DeviceStrings.DRYING_MINUTES, (it + 59) / 60) } ?: DeviceStrings.DRYING

internal fun roomTip(selected: Int): String =
    if (selected == 0) DeviceStrings.TIP_SELECT_ROOMS else fill(DeviceStrings.TIP_ROOMS_SELECTED, selected)

internal fun tabLabel(tab: MainTab): String = when (tab) {
    MainTab.FULL -> DeviceStrings.TAB_FULL
    MainTab.ROOM -> DeviceStrings.TAB_ROOM
    MainTab.ZONE -> DeviceStrings.TAB_ZONE
    MainTab.ROUTINE -> DeviceStrings.TAB_ROUTINE
}

/** The tab to show: the remembered one if still offered, else Full. */
internal fun resolveTab(remembered: MainTab, tabs: List<MainTab>): MainTab = if (remembered in tabs) remembered else MainTab.FULL

/** The mode chip's title, following the current mode as the official chip does. */
internal fun modeChipLabel(fanPower: Int?, waterMode: Int?, route: Int?, seqType: Int?): String = when {
    isSmartModeSet(fanPower, waterMode, route) -> DeviceStrings.MODE_SMARTPLAN
    isCustomizeModeSet(fanPower, waterMode, route) -> DeviceStrings.MODE_CUSTOMIZE
    seqType == 1 -> DeviceStrings.MODE_VAC_THEN_MOP
    fanPower == VacuumFanPower.OFF -> DeviceStrings.MODE_MOP
    waterMode == WaterBoxMode.OFF -> DeviceStrings.MODE_VACUUM
    else -> DeviceStrings.MODE_VAC_AND_MOP
}

/** General sub-tabs of the mode sheet, official order. */
internal enum class GeneralMode { VAC_THEN_MOP, VAC_AND_MOP, MOP, VACUUM }

internal fun generalModes(cleanThenMop: Boolean, pureMop: Boolean, currentlyMop: Boolean): List<GeneralMode> = buildList {
    if (cleanThenMop) add(GeneralMode.VAC_THEN_MOP)
    add(GeneralMode.VAC_AND_MOP)
    // A robot that forbids pure mop still shows the tab while it is set, so it can be left.
    if (pureMop || currentlyMop) add(GeneralMode.MOP)
    add(GeneralMode.VACUUM)
}

internal fun currentGeneralMode(fanPower: Int?, waterMode: Int?, seqType: Int?): GeneralMode = when {
    seqType == 1 -> GeneralMode.VAC_THEN_MOP
    (waterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF -> GeneralMode.VACUUM
    fanPower == VacuumFanPower.OFF -> GeneralMode.MOP
    else -> GeneralMode.VAC_AND_MOP
}

internal fun generalModeLabel(mode: GeneralMode): String = when (mode) {
    GeneralMode.VAC_THEN_MOP -> DeviceStrings.MODE_VAC_THEN_MOP
    GeneralMode.VAC_AND_MOP -> DeviceStrings.MODE_VAC_AND_MOP
    GeneralMode.MOP -> DeviceStrings.MODE_MOP
    GeneralMode.VACUUM -> DeviceStrings.MODE_VACUUM
}

internal fun generalModeTip(mode: GeneralMode): String = when (mode) {
    GeneralMode.VAC_THEN_MOP -> DeviceStrings.TIP_VAC_THEN_MOP
    GeneralMode.VAC_AND_MOP -> DeviceStrings.TIP_VAC_AND_MOP
    GeneralMode.MOP -> DeviceStrings.TIP_MOP
    GeneralMode.VACUUM -> DeviceStrings.TIP_VACUUM
}

/** Suction and water to send when switching to [mode]; null keeps the current value. */
internal data class MotorChange(val fanPower: Int?, val waterMode: Int?)

internal fun motorChangeFor(mode: GeneralMode, fanPower: Int?, waterMode: Int?): MotorChange {
    val waterOff = (waterMode ?: WaterBoxMode.OFF) == WaterBoxMode.OFF
    val fanOff = fanPower == VacuumFanPower.OFF
    return when (mode) {
        GeneralMode.VAC_AND_MOP, GeneralMode.VAC_THEN_MOP -> MotorChange(
            fanPower = if (fanOff) VacuumFanPower.BALANCED else null,
            waterMode = if (waterOff) WaterBoxMode.LOW else null,
        )
        GeneralMode.MOP -> MotorChange(VacuumFanPower.OFF, if (waterOff) WaterBoxMode.LOW else null)
        GeneralMode.VACUUM -> MotorChange(if (fanOff) VacuumFanPower.BALANCED else null, WaterBoxMode.OFF)
    }
}

/** Without clean efficiency the robot takes only the Standard and Fast routes in Vac followed by Mop, so others fall back to Standard. */
internal fun routeForVacThenMop(route: Int, cleanEfficiency: Boolean): Int =
    if (!cleanEfficiency && route != MopRoute.STANDARD && route != MopRoute.FAST) MopRoute.STANDARD else route

/** State 26 worded by back_type, the official back-dock substates (status-and-enums.md 3.5). */
internal fun backDockText(backType: Int?): String = when (backType) {
    1 -> DeviceStrings.STATUS_DOCKING_TO_WASH
    2 -> DeviceStrings.STATUS_DOCKING_TO_SETUP_MOP
    3 -> DeviceStrings.STATUS_DOCKING_TO_DETACH_MOP
    4 -> DeviceStrings.STATUS_DOCKING_TO_EMPTY
    else -> DeviceStrings.STATUS_GOING_TO_WASH
}

/** What the Dock button offers mid-clean on robots that can dock halfway (bit 85 and a wash or collect dock). */
internal enum class DockOption(val label: String) {
    BACK_TO_DOCK(DeviceStrings.BACK_TO_DOCK),
    MIDWAY_EMPTY(DeviceStrings.MIDWAY_EMPTY),
    MIDWAY_WASH(DeviceStrings.MIDWAY_WASH),
}

/** Empty when the Dock button acts directly; otherwise the choices to offer, official order. */
internal fun dockMenuOptions(state: Int?, inCleaning: Int?, midwayDocking: Boolean, dockType: Int?): List<DockOption> {
    val cleaningJob = state in ACTIVE_CLEANING_STATES || (state == VacuumStateCodes.PAUSED && (inCleaning ?: 0) != 0)
    val hasDock = dockType != null && dockType != 0
    if (!midwayDocking || !hasDock || !cleaningJob || dockAction(state) != DockAction.RETURN) return emptyList()
    return listOf(DockOption.MIDWAY_EMPTY, DockOption.MIDWAY_WASH, DockOption.BACK_TO_DOCK)
}
