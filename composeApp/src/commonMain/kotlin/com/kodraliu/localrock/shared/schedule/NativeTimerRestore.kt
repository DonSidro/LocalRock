package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The robot's cron for a server job's cron: the job writes `?` for the unused field, the robot `*`. */
internal fun robotCronOf(jobCron: String): String? {
    val fields = jobCron.trim().split(Regex("\\s+"))
    if (fields.size != 5) return null
    return fields.joinToString(" ") { if (it == "?") "*" else it }
}

/** Settings a restore can rebuild exactly; anything else would be dropped, so restore refuses it. */
internal val RESTORABLE_INT_SETTINGS = setOf("fan_power", "water_box_mode", "mop_mode", "repeat", "clean_mop", "map_index")

/** Why a missing native timer cannot be put back from [job], or null when it can. */
internal fun restoreBlockedReason(job: ServerSchedule): String? {
    val settings = job.settings
    val segments = settings?.get("segments")
    val unknown = settings?.keys.orEmpty() - RESTORABLE_INT_SETTINGS - setOf("name", "segments")
    return when {
        job.nativeName.isNullOrBlank() -> "This schedule has no native timer reference, so there is nothing to restore."
        (job.param?.get("method") as? JsonPrimitive)?.contentOrNull != "server_scheduled_start" || settings == null ->
            "This schedule's format is not supported, so it was not restored."
        robotCronOf(job.cron) == null -> "This schedule's time could not be read, so it was not restored."
        segments != null && (segments !is JsonPrimitive || !segments.isString) ->
            "This schedule's room selection is in a format that can't be restored. Delete and recreate this schedule."
        // The robot numbers rooms differently from the job, so only whole-home entries can be rebuilt.
        job.settingsText("segments").let { it.isNotBlank() && it != "0" } ->
            "Room schedules can't be restored because the robot numbers rooms differently. Delete and recreate this schedule."
        unknown.isNotEmpty() ->
            "This schedule has settings the app can't restore (${unknown.sorted().joinToString()}). Nothing was restored."
        RESTORABLE_INT_SETTINGS.any { key -> settings[key]?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() == null } == true } ->
            "This schedule's cleaning settings could not be read, so it was not restored."
        else -> null
    }
}

/**
 * Puts back a server job's missing robot timer. Works from a fresh job read, never resends a write,
 * reads back anything uncertain, and checks both the robot and the server job before reporting success.
 */
class NativeTimerRestoration(
    private val readJobs: suspend () -> List<ServerSchedule>,
    private val readTimers: suspend () -> JsonElement?,
    private val createTimer: suspend (name: String, cron: String, settings: JsonObject) -> Unit,
    private val writeTimer: suspend (String, Boolean) -> Unit,
    private val writeJob: suspend (ServerSchedule, JsonObject) -> Unit,
    private val isRefusal: (Exception) -> Boolean,
) {
    suspend fun restore(expected: ServerSchedule): String {
        val job = freshJob(expected, enabledMayDiffer = false)
        restoreBlockedReason(job)?.let { throw ScheduleException(it) }
        val name = requireNotNull(job.nativeName)
        val wanted = if (job.enabled) "On" else "Off"
        var state = robotState(name)
        // A previous restore that created the timer but could not turn it off is finished here.
        val finishing = state == "On" && wanted == "Off"
        if (state != "Missing" && !finishing) throw ScheduleException("Native timer is ${state.lowercase()}; nothing was restored.")

        if (!finishing) {
            try {
                request { createTimer(name, requireNotNull(robotCronOf(job.cron)), requireNotNull(job.settings)) }
                state = "On" // the robot creates a timer switched on
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isRefusal(e)) throw ScheduleException("${e.message}. Nothing was restored.")
                // A lost reply isn't proof of failure: read back once, never resend.
                state = readBack(name, "The restore was sent but the robot's reply was lost")
                if (state == "Missing") throw ScheduleException("The restore was not applied; the robot timer is still missing. Refresh before retrying.")
            }
        }

        if (state == "On" && wanted == "Off") {
            freshJob(expected, enabledMayDiffer = true) // the job must not have been edited meanwhile
            try { request { writeTimer(name, false) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Read-back decides. */ }
        }
        state = readBack(name, "The robot timer was restored, but its state is unconfirmed")
        if (state != wanted) {
            throw ScheduleException(
                if (wanted == "Off" && state == "On") "Partly restored: the robot timer is back but still on, while this schedule is off. Open the schedule, tap Check native timer, then Finish restore."
                else "Restore was sent, but the robot reports the timer as ${state.lowercase()}. Refresh before retrying.",
            )
        }
        confirmServerJob(expected)
        return state
    }

    private suspend fun freshJob(expected: ServerSchedule, enabledMayDiffer: Boolean): ServerSchedule {
        val jobs = request { readJobs() }
        val matches = jobs.filter { it.id == expected.id }
        if (matches.size > 1) throw ScheduleException("Ambiguous server job ID. Refresh; nothing was restored.")
        val job = matches.singleOrNull() ?: throw ScheduleException("This schedule was deleted. Refresh; nothing was restored.")
        if (!sameJob(job, expected, enabledMayDiffer)) throw ScheduleException("This schedule changed since it was loaded. Refresh before restoring.")
        val name = job.nativeName
        if (name != null && jobs.count { it.nativeName == name } > 1) {
            throw ScheduleException("Multiple server jobs share this native timer. Resolve the conflicting schedules first; nothing was restored.")
        }
        return job
    }

    // Server reconciliation may have copied the robot's brief "on" into the job; put the saved state back.
    private suspend fun confirmServerJob(expected: ServerSchedule) {
        val job = try { freshJob(expected, enabledMayDiffer = true) }
        catch (e: ScheduleException) { throw ScheduleException("The robot timer was restored. ${e.message}") }
        if (job.enabled == expected.enabled) return
        try { request { writeJob(job, JsonObject(mapOf("enabled" to JsonPrimitive(expected.enabled)))) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Read-back decides. */ }
        val after = try { freshJob(expected, enabledMayDiffer = true) } catch (e: ScheduleException) { null }
        if (after?.enabled != expected.enabled) {
            throw ScheduleException("The robot timer was restored, but the server now shows this schedule as ${if (after?.enabled == true) "on" else "unconfirmed"}. Refresh and check it.")
        }
    }

    private suspend fun robotState(name: String): String = nativeTimerState(request { readTimers() }, name)

    private suspend fun readBack(name: String, context: String): String = try { robotState(name) }
    catch (e: CancellationException) { throw e }
    catch (_: Exception) { throw ScheduleException("$context, and reading it back failed. Refresh before retrying; nothing was resent.") }

    private fun sameJob(a: ServerSchedule, b: ServerSchedule, enabledMayDiffer: Boolean): Boolean {
        fun view(j: ServerSchedule) = j.snapshot().toMutableMap().apply {
            j.param?.let { put("param", it) }
            if (enabledMayDiffer) remove("enabled")
        }
        return view(a) == view(b)
    }

    /** Session timeouts cancel an inner await, not this restore. Preserve actual caller cancellation. */
    private suspend fun <T> request(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw ScheduleException("Connection closed or request timed out.")
    }
}
