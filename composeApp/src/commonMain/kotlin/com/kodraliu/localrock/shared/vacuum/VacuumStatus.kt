package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class VacuumStatus(
    val battery: Int? = null,
    val state: Int? = null,
    @SerialName("fan_power") val fanPower: Int? = null,
    @SerialName("clean_area") val cleanArea: Long? = null,
    @SerialName("clean_time") val cleanTime: Long? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("water_box_status") val waterBoxStatus: Int? = null,

    @SerialName("water_box_mode") val waterBoxCustomMode: Int? = null,
    @SerialName("charge_status") val chargeStatus: Int? = null,
    @SerialName("dock_error_status") val dockErrorStatus: Int? = null,
    @SerialName("dry_status") val dryStatus: Int? = null,
    @SerialName("rdt") val remainingDryTimeSec: Long? = null,
)


object DockErrorCodes {
    const val OK = 0
    const val DUCT_BLOCKAGE = 34
    const val WATER_EMPTY = 38
    const val DIRTY_TANK_FULL = 39
    const val DIRTY_TANK_LATCH_OPEN = 44
    const val NO_DUSTBIN = 46
    const val CLEANING_TANK_FULL_OR_BLOCKED = 53
}


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
    const val MAPPING = 22
    const val NOT_CHARGING = 24
    const val WASHING_MOP = 23
    const val GOING_TO_WASH_MOP = 26
    const val DRYING_MOP = 27
    const val RETURNING_TO_DOCK_FOR_DRYING = 28
    const val RETURNING_TO_WASH_MOP = 29
}

object VacuumErrorCodes {
    fun describe(code: Int): String = when (code) {
        0 -> ""
        1 -> "Laser sensor fault"
        2 -> "Collision sensor fault"
        3 -> "Wheel floating"
        4 -> "Cliff sensor fault"
        5 -> "Main brush blocked"
        6 -> "Side brush blocked"
        7 -> "Wheel blocked"
        8 -> "Robot stuck"
        9 -> "Dustbin missing"
        10 -> "Filter blocked"
        11 -> "Magnetic field detected"
        12 -> "Low battery"
        13 -> "Charging problem"
        14 -> "Battery failure"
        15 -> "Wall sensor fault"
        16 -> "Uneven surface"
        17 -> "Side brush failure"
        18 -> "Suction fan failure"
        19 -> "Unpowered dock"
        21 -> "Laser pressure sensor problem"
        22 -> "Charge sensor problem"
        23 -> "Dock problem"
        24 -> "No-go zone or invisible wall detected"
        25 -> "Mop pad dirty"
        26 -> "Mop wash tank empty"
        27 -> "Dirty water tank full"
        28 -> "Mop pad not installed"
        29 -> "Battery overheating"
        30 -> "Mop pad blocked"
        31 -> "Mop pad not installed"
        32 -> "Dustbin not installed"
        else -> "Error code $code"
    }
}


object VacuumFanPower {
    const val QUIET = 101
    const val BALANCED = 102
    const val TURBO = 103
    const val MAX = 104
}


object WaterBoxMode {
    const val OFF = 200
    const val LOW = 201
    const val MEDIUM = 202
    const val HIGH = 203
}


object MopRoute {
    const val STANDARD = 300
    const val DEEP = 301
    const val DEEP_PLUS = 303
    const val FAST = 304
}
