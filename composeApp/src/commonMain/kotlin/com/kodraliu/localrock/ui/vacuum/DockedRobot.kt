package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

private val DOCK_STATES = setOf(
    VacuumStateCodes.CHARGING,
    VacuumStateCodes.EMPTYING_BIN,
    VacuumStateCodes.WASHING_MOP,
    VacuumStateCodes.DRYING_MOP,
    VacuumStateCodes.CHARGING_COMPLETE,
)

/** Charging, or emptying, washing or drying at the dock; charge_status is not used, it reads 1 off the dock too. */
internal fun isDocked(state: Int?): Boolean = state in DOCK_STATES

/** The map with the robot at its charger while it says it is docked; its map position lags until it re-localizes. */
internal fun ParsedMap.withRobotOnDock(docked: Boolean): ParsedMap {
    val charger = chargerMm ?: return this
    return if (docked && robotMm != charger) copy(robotMm = charger) else this
}
