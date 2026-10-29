package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.map.LegacyMapRevision
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Robot-reported run kind; UI selections, schedules and robot position are never inputs. */
internal fun VacuumStatus.mapRunKind(): Int? {
    when (state) {
        VacuumStateCodes.MAPPING, VacuumStateCodes.SPOT_CLEANING, VacuumStateCodes.GOING_TO_TARGET,
        VacuumStateCodes.MANUAL_MODE, VacuumStateCodes.REMOTE_CONTROL_ACTIVE -> return null
    }
    if (inCleaning != null) return inCleaning.takeIf { it in 1..3 }
    return when (state) {
        VacuumStateCodes.ZONED_CLEANING -> 2
        VacuumStateCodes.SEGMENT_CLEANING -> 3
        else -> null
    }
}

/**
 * Reject a map requested before a run/floor change, and an unchanged cached map from the preceding
 * run. A missing or malformed target block means unknown, never the last UI selection.
 * Immutable so the repository can update it atomically alongside concurrent map/status responses.
 */
internal data class ActiveRunRooms(
    val status: VacuumStatus = VacuumStatus(),
    val generation: Long = 0,
    val roomIds: Set<Int>? = null,
    val lastMapRevision: LegacyMapRevision? = null,
    val previousRunMapRevision: LegacyMapRevision? = null,
    val statusKnown: Boolean = false,
) {
    fun withStatus(fresh: VacuumStatus): ActiveRunRooms {
        val kind = fresh.mapRunKind()
        val timeReset = status.cleanTime != null && fresh.cleanTime != null && fresh.cleanTime < status.cleanTime
        val boundary = kind != status.mapRunKind() || fresh.loadedMapFlag != status.loadedMapFlag || timeReset
        return copy(
            status = fresh,
            statusKnown = true,
            generation = if (boundary) generation + 1 else generation,
            roomIds = if (boundary || kind == null) null else roomIds,
            previousRunMapRevision = if (boundary) lastMapRevision else previousRunMapRevision,
        )
    }

    fun invalidate(): ActiveRunRooms = copy(generation = generation + 1, roomIds = null, statusKnown = false)

    /** DPS pushes lack run scope. A state change needs a full status read; battery-only pushes
     * cannot restore trust after a failed read or silently reuse the preceding task's scope. */
    fun withStatePush(state: Int?): ActiveRunRooms =
        if (state != null && state != status.state) invalidate() else this

    fun withMap(map: ParsedMap?, requestedGeneration: Long): ActiveRunRooms {
        if (requestedGeneration != generation) return this
        val repeatedPreviousMap = map?.legacyRevision != null && map.legacyRevision == previousRunMapRevision
        val ids = if (!statusKnown || map == null || repeatedPreviousMap) null else when (status.mapRunKind()) {
            // Only explicit in_cleaning=1 establishes a full-home task. State=Cleaning alone
            // does not establish its scope. Zoned cleaning never implies a set of whole rooms.
            1 -> map.rooms.map { it.id }.toSet().takeIf { it.isNotEmpty() }
            3 -> map.reportedCleanSegments?.takeIf { reported ->
                reported.isNotEmpty() && reported.all { id -> map.rooms.any { it.id == id } }
            }
            else -> null
        }
        return copy(roomIds = ids, lastMapRevision = map?.legacyRevision ?: lastMapRevision)
    }
}
