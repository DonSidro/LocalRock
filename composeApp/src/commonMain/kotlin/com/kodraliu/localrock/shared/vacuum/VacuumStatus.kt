package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class VacuumStatus(
    val battery: Int? = null,
    val state: Int? = null,
    @SerialName("fan_power") val fanPower: Int? = null,
    @SerialName("seq_type") val cleanSequenceType: Int? = null,
    @SerialName("clean_area") val cleanArea: Long? = null,
    @SerialName("clean_time") val cleanTime: Long? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("water_box_status") val waterBoxStatus: Int? = null,

    @SerialName("water_box_mode") val waterBoxCustomMode: Int? = null,
    @SerialName("charge_status") val chargeStatus: Int? = null,
    @SerialName("dock_error_status") val dockErrorStatus: Int? = null,
    @SerialName("dock_type") val dockType: Int? = null,
    @SerialName("dss") val dockStatus: Int? = null,
    @SerialName("dry_status") val dryStatus: Int? = null,
    @SerialName("rdt") val remainingDryTimeSec: Long? = null,
    /** Loaded map in the upper bits (see [loadedMapFlag]). */
    @SerialName("map_status") val mapStatus: Int? = null,
    /**
     * Bit [LAB_STATUS_MAP_SAVING] set while the robot keeps maps, bit [LAB_STATUS_MULTI_LEVEL] while
     * multi-level maps are on (captured 3 and 1; 0 on a robot that never met the official app).
     */
    @SerialName("lab_status") val labStatus: Int? = null,
    /** 0 = recognise the floor automatically, 1 = pick the map by hand (captured). */
    @SerialName("switch_map_mode") val switchMapMode: Int? = null,
    /**
     * 0 once a clean job is over. Stays non-zero through the breaks inside one (recharging,
     * washing the mop), which is how the end of a job is told from a pause in it.
     */
    @SerialName("in_cleaning") val inCleaning: Int? = null,
    /** How far through the current job the robot says it is, 0..100. */
    @SerialName("clean_percent") val cleanPercent: Int? = null,
) {
    val multiLevelEnabled: Boolean? get() = labStatus?.let { it and LAB_STATUS_MULTI_LEVEL != 0 }

    /** False on a robot that will not store a map: it refuses manual_segment_map (issue #17). */
    val mapSavingEnabled: Boolean? get() = labStatus?.let { it and LAB_STATUS_MAP_SAVING != 0 }

    /**
     * mapFlag of the map the robot has loaded: map_status >> 2 (captured 2026-09-29: 7 with map 1
     * loaded, 11 with map 2), or [UNSAVED_MAP_FLAG] while a new map is being built and not saved.
     */
    val loadedMapFlag: Int? get() = mapStatus?.let { it shr 2 }

    /**
     * True while the robot holds a newly built map that has not been saved. Bit 0 of map_status
     * follows map_present: 253 after a mapping run, 252 on a robot with no map at all.
     */
    val unsavedMapPresent: Boolean
        get() = loadedMapFlag == UNSAVED_MAP_FLAG && mapStatus?.and(MAP_STATUS_PRESENT) != 0
}

const val LAB_STATUS_MAP_SAVING = 1
const val LAB_STATUS_MULTI_LEVEL = 2
const val SWITCH_MAP_MODE_SMART = 0
const val SWITCH_MAP_MODE_MANUAL = 1

/** loadedMapFlag when no saved map is loaded: after a mapping run, or with no map at all (map_status 252-255). */
const val UNSAVED_MAP_FLAG = 63
/** map_status bit set while the robot holds any map (0 with map_present 0). */
const val MAP_STATUS_PRESENT = 1


object DockErrorCodes {
    const val OK = 0
    const val DUCT_BLOCKAGE = 34
    const val WATER_EMPTY = 38
    const val DIRTY_TANK_FULL = 39
    const val DIRTY_TANK_LATCH_OPEN = 44
    const val NO_DUSTBIN = 46
    const val CLEANING_TANK_FULL_OR_BLOCKED = 53

    fun describe(code: Int): String = when (code) {
        OK -> ""
        DUCT_BLOCKAGE -> "Dock dust duct blocked"
        WATER_EMPTY -> "Clean water tank empty"
        DIRTY_TANK_FULL -> "Dirty water tank full"
        DIRTY_TANK_LATCH_OPEN -> "Dirty water tank latch open"
        NO_DUSTBIN -> "Dock dust bag missing"
        CLEANING_TANK_FULL_OR_BLOCKED -> "Dock cleaning tray full or blocked"
        else -> VacuumErrorCodes.describe(code).takeIf { !it.startsWith("Error code") } ?: "Dock error $code"
    }
}


/** True when the robot sits on its dock and is not in the middle of a clean. */
fun VacuumStatus.isDockedAndIdle(): Boolean =
    inCleaning == 0 && state in setOf(VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE)

object VacuumStateCodes {
    const val STARTING = 1
    const val CHARGER_DISCONNECTED = 2
    const val IDLE = 3
    const val REMOTE_CONTROL_ACTIVE = 4
    const val CLEANING = 5
    const val RETURNING_HOME = 6
    const val MANUAL_MODE = 7
    const val CHARGING = 8
    const val CHARGING_ERROR = 9
    const val PAUSED = 10
    const val SPOT_CLEANING = 11
    const val ERROR = 12
    const val SHUTTING_DOWN = 13
    const val UPDATING = 14
    const val DOCKING = 15
    const val GOING_TO_TARGET = 16
    const val ZONED_CLEANING = 17
    const val SEGMENT_CLEANING = 18
    const val EMPTYING_BIN = 22
    const val NOT_CHARGING = 24
    const val WASHING_MOP = 23

    /** A second code for washing the mop; the official app names both the same. */
    const val WASHING_MOP_ALT = 25
    const val GOING_TO_WASH_MOP = 26
    const val DRYING_MOP = 27

    /** The official Roborock app and python-roborock both name 28 "in call" (a live video call is open). */
    const val IN_CALL = 28
    /** Captured 2026-09-29: the robot reports 29 for the whole app_start_build_map run. */
    const val MAPPING = 29

    /** Reported while the robot patrols with its camera: the pet search and Cruise. */
    const val PATROLLING = 32
    const val EGG_ATTACK = 30
    const val SETUP_MOP = 33
    const val TEARDOWN_MOP = 34
    const val CHARGING_COMPLETE = 100
    const val OFFLINE = 101
    const val LOCKED = 103

    fun describe(state: Int): String = when (state) {
        0 -> "Getting info"
        STARTING -> "Starting"
        CHARGER_DISCONNECTED -> "Sleeping"
        IDLE -> "Idle"
        REMOTE_CONTROL_ACTIVE -> "Remote control"
        CLEANING -> "Cleaning"
        RETURNING_HOME -> "Returning home"
        MANUAL_MODE -> "Remote controlling"
        CHARGING -> "Charging"
        CHARGING_ERROR -> "Charging error"
        PAUSED -> "Paused"
        PATROLLING -> "On cruise"
        SPOT_CLEANING -> "Spot cleaning"
        ERROR -> "Error"
        SHUTTING_DOWN -> "Shutting down"
        UPDATING -> "Updating"
        DOCKING -> "Docking"
        GOING_TO_TARGET -> "Going to target"
        ZONED_CLEANING -> "Zoned cleaning"
        SEGMENT_CLEANING -> "Room cleaning"
        MAPPING -> "Mapping"
        NOT_CHARGING -> "Idle"
        WASHING_MOP, WASHING_MOP_ALT -> "Washing mop"
        GOING_TO_WASH_MOP -> "Going to wash mop"
        DRYING_MOP -> "Drying mop"
        IN_CALL -> "In call"
        EGG_ATTACK -> "Scanning"
        SETUP_MOP -> "Reinstalling mop cloth mounts"
        TEARDOWN_MOP -> "Detaching mop cloth mounts"
        36 -> "Demo mode"
        37 -> "Boogie mode"
        38 -> "Sorting"
        39 -> "Remote control to pick up"
        40 -> "Emergency stopped"
        41 -> "Resetting"
        42 -> "Programming mode"
        43 -> "Changing the mop"
        EMPTYING_BIN -> "Emptying the bin"
        CHARGING_COMPLETE -> "Fully charged"
        OFFLINE -> "Offline"
        LOCKED -> "Saving map"
        else -> "State $state"
    }
}

object VacuumErrorCodes {
    /** The official app's titles; the same numbering covers robot and dock errors. */
    fun describe(code: Int): String = when (code) {
        0 -> ""
        1 -> "LiDAR sensor error"
        2 -> "Bumper stuck"
        3 -> "Wheels suspended"
        4 -> "Cliff sensor error"
        5 -> "Main brush jammed"
        6 -> "Side brush jammed"
        7 -> "The robot is stuck, or main wheels are jammed"
        8 -> "The robot is trapped or stuck"
        9 -> "Dustbin not installed"
        10 -> "Filter is wet or blocked"
        11 -> "Strong magnetic field detected"
        12 -> "Low battery"
        13 -> "Charging error"
        14 -> "Temperature protection"
        15 -> "The wall sensor is dirty"
        16 -> "Robot is tilted"
        17 -> "Side brush error"
        18 -> "Vacuum fan error"
        19 -> "Dock not connected to power"
        20 -> "The optical flow sensor is dirty"
        21 -> "Vertical bumper error"
        22 -> "Dock locator error"
        23 -> "Unable to return to dock"
        24 -> "No-go zone or invisible wall detected"
        25 -> "Camera error"
        26 -> "Wall sensor error"
        27 -> "Jammed mop module"
        28 -> "The robot may be on a carpet"
        29 -> "Unable to cross the carpet"
        31 -> "Front and rear sensors error"
        32 -> "No dustbin or filter installed"
        34 -> "Clean the auto-empty dock bin"
        35 -> "Auto-empty dock voltage error"
        36 -> "Front sensor dirty"
        37 -> "Rear sensor dirty"
        38 -> "Check the clean water tank"
        39 -> "Check the dirty water tank"
        40 -> "Water filter not installed"
        41 -> "Unable to open the electric hatch"
        42 -> "Check that the water filter is correctly installed"
        43 -> "Positioning button error"
        44 -> "Check and secure the dirty water tank cover"
        45 -> "Unable to close the electric hatch"
        46 -> "Check that the dust bag is properly installed"
        48 -> "Refill error"
        49 -> "Drain error"
        51 -> "Unit temperature protection"
        52 -> "Please check the cleaning tray"
        53 -> "Cleaning tray full"
        54 -> "The mop mount fell off"
        55 -> "Please check the cleaning tray"
        56 -> "Jammed mop module"
        57 -> "Cleaning tank maintenance brush error"
        59 -> "Main wheels jammed"
        60 -> "Omnidirectional wheel jammed"
        61 -> "Check whether the LiDAR sensor can lift and lower freely"
        62 -> "Check the water filter of the cleaning sink"
        76 -> "Roller mop not installed"
        77 -> "Dirty water channel not installed"
        78 -> "On-board dirty water tank not installed"
        79 -> "Roller mop fell off"
        80 -> "Dirty water channel fell off"
        81 -> "On-board dirty water tank fell off"
        82 -> "Roller mop cover error"
        83 -> "Drain actuator error"
        84 -> "On-board dirty water tank drain error"
        85 -> "Mop cloth mount not installed"
        86 -> "Climbing arm jammed"
        95 -> "Mop cloth mount installation failed"
        97 -> "Check the cleaning solution level"
        98 -> "LiDAR sensor dirty"
        99 -> "Please check the cleaning tray"
        141 -> "The robot is stuck, or main wheels are jammed"
        143 -> "The robot is stuck, or main wheels are jammed"
        154 -> "Side brush error"
        155 -> "Vacuum fan error"
        644 -> "Empty the dustbin"
        100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 128, 129, 164, 165, 166, 167, 169, 171, 172, 253, 254, 255 -> "Internal error. Reset the robot."
        126, 127, 130, 131, 134, 145, 147, 148, 149, 150, 151, 152, 153, 156, 157, 158, 159, 160, 163 -> "Dock error. Unplug the dock and plug it back in."
        else -> "Error code $code"
    }
}


object VacuumFanPower {
    const val QUIET = 101
    const val BALANCED = 102
    const val TURBO = 103
    const val MAX = 104
    /** No suction: what the official app sets for its Mop tab (captured on an S8 Pro Ultra). */
    const val OFF = 105
    const val CUSTOM = 106
    const val SMART = 110
}


object WaterBoxMode {
    const val OFF = 200
    const val LOW = 201
    const val MEDIUM = 202
    const val HIGH = 203
    const val CUSTOM = 204
    const val SMART = 209
    const val SLIDE_MEDIUM = 235

    /** Pure-water-flow robots (e.g. Saros 10R) report 201/202/203 back as 225/235/245. */
    fun normalize(code: Int?): Int? = when (code) {
        225 -> LOW
        SLIDE_MEDIUM -> MEDIUM
        245 -> HIGH
        else -> code
    }
}


object MopRoute {
    const val STANDARD = 300
    const val DEEP = 301
    const val CUSTOM = 302
    const val DEEP_PLUS = 303
    const val FAST = 304
    const val SMART = 306
}
