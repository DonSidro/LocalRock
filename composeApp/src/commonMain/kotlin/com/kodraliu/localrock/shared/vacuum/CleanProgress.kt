package com.kodraliu.localrock.shared.vacuum

/**
 * How far through its current clean job a robot is, derived from [VacuumStatus].
 *
 * [percent] is [VacuumStatus.cleanPercent] clamped to 0..100, and null while no job is running
 * (in_cleaning 0) or on a robot that never reports the field. Monotonicity within one run is the
 * caller's concern: the robot can answer a poll with a lower figure than the last one, and a bar
 * that moves backwards reads as a bug, so a consuming flow should keep the max until the job
 * ends.
 */
data class CleanProgress(
    /** 0..100, or null when the job is over or the robot does not report it. */
    val percent: Int?,
    /** True from the first cleaning state until the job is over, through mid-job breaks. */
    val jobActive: Boolean,
    /** What the robot is doing right now; a break freezes [percent] but changes the phase. */
    val phase: Phase,
) {
    enum class Phase {
        /** Actively cleaning: vacuuming, mopping, spot, zone, segment. */
        Cleaning,

        /** Paused by the user mid-job. */
        Paused,

        /**
         * Mid-job break: returning to the dock to recharge, washing or drying the mop. The robot
         * is not cleaning and the percentage is frozen, but the job is not over.
         */
        Break,

        /** The job has finished, been stopped, the robot is reporting an error, or it docked. */
        Over,
    }
}

/** The states in which the robot is actually cleaning; breaks are a different [CleanProgress.Phase]. */
internal val ACTIVE_CLEANING_STATES = setOf(
    VacuumStateCodes.CLEANING,
    VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.ZONED_CLEANING,
    VacuumStateCodes.SPOT_CLEANING,
)

/**
 * Mid-job states the robot passes through *inside* one clean job: heading back to the dock to
 * recharge, washing or drying the mop, emptying the bin. They are not the end, so a progress
 * display must stay up through them with its percent frozen; the end is [VacuumStatus.jobOver]
 * once in_cleaning clears.
 */
internal val MID_JOB_STATES = setOf(
    VacuumStateCodes.RETURNING_HOME,
    VacuumStateCodes.CHARGING,
    VacuumStateCodes.CHARGING_COMPLETE,
    VacuumStateCodes.WASHING_MOP,
    VacuumStateCodes.GOING_TO_WASH_MOP,
    VacuumStateCodes.DRYING_MOP,
    VacuumStateCodes.RETURNING_TO_DOCK_FOR_DRYING,
    VacuumStateCodes.EMPTYING_BIN,
)

/**
 * True while a clean job is running: the robot is cleaning, paused, or on a mid-job break, and
 * [VacuumStatus.inCleaning] is non-zero. in_cleaning stays set through the breaks inside one job
 * (recharging, washing the mop) and goes to 0 only when the job is over — the same boundary the
 * notifications use. A robot that does not report it at all is taken at its word on the state
 * alone, which is the same behavior [com.kodraliu.localrock.shared.notify.RobotMonitor] uses.
 */
internal val VacuumStatus.jobActive: Boolean
    get() = state in (ACTIVE_CLEANING_STATES + MID_JOB_STATES + setOf(VacuumStateCodes.PAUSED)) &&
        (inCleaning ?: 1) != 0

/**
 * Derive the job progress for the current [status]. Pure, so it is testable without Compose.
 */
fun VacuumStatus.toCleanProgress(): CleanProgress {
    if (!jobActive) {
        return CleanProgress(percent = null, jobActive = false, phase = CleanProgress.Phase.Over)
    }
    val phase = when (state) {
        VacuumStateCodes.PAUSED -> CleanProgress.Phase.Paused
        in MID_JOB_STATES -> CleanProgress.Phase.Break
        in ACTIVE_CLEANING_STATES -> CleanProgress.Phase.Cleaning
        else -> CleanProgress.Phase.Over
    }
    val percent = cleanPercent?.coerceIn(0, 100)
    return CleanProgress(percent = percent, jobActive = true, phase = phase)
}
