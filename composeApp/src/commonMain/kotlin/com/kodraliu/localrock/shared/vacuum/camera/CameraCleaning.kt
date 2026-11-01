package com.kodraliu.localrock.shared.vacuum.camera

import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes

enum class CameraCleaningAction(val label: String) {
    START("Start cleaning"), RESUME("Resume cleaning"), PAUSE("Pause cleaning"),
}

/** Video Play controls the cleaning task. It is usable before a docked robot can stream. */
fun cameraCleaningAction(state: Int?, inCleaning: Int?, errorCode: Int?): CameraCleaningAction? {
    if (errorCode != 0) return null
    return when (state) {
        VacuumStateCodes.CLEANING, VacuumStateCodes.ZONED_CLEANING,
        VacuumStateCodes.SEGMENT_CLEANING, VacuumStateCodes.SPOT_CLEANING -> CameraCleaningAction.PAUSE
        VacuumStateCodes.PAUSED -> when (inCleaning) {
            1, 2, 3 -> CameraCleaningAction.RESUME
            0 -> CameraCleaningAction.START
            else -> null
        }
        VacuumStateCodes.IDLE, VacuumStateCodes.CHARGER_DISCONNECTED, VacuumStateCodes.NOT_CHARGING,
        VacuumStateCodes.CHARGING, VacuumStateCodes.CHARGING_COMPLETE -> when (inCleaning) {
            0 -> CameraCleaningAction.START
            1, 2, 3 -> CameraCleaningAction.RESUME
            else -> null
        }
        else -> null
    }
}

/** Match the requested action against a fresh status; a stale Play must never become Pause. */
internal fun cameraCleaningCommand(requested: CameraCleaningAction, state: Int?, inCleaning: Int?, errorCode: Int?): String? {
    if (cameraCleaningAction(state, inCleaning, errorCode) != requested) return null
    return when (requested) {
        CameraCleaningAction.START -> "app_start"
        CameraCleaningAction.PAUSE -> "app_pause"
        CameraCleaningAction.RESUME -> when (inCleaning) {
            1 -> "app_start"
            2 -> "resume_zoned_clean"
            3 -> "resume_segment_clean"
            else -> null
        }
    }
}
