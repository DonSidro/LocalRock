package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.*

/** No retries or rollback: every write is preceded and followed by fresh reads. */
class ScheduleEnablement(
    private val readJobs: suspend () -> List<ServerSchedule>,
    private val writeJob: suspend (ServerSchedule, JsonObject) -> Unit,
    private val readTimers: suspend () -> JsonElement?,
    private val writeTimer: suspend (String, Boolean) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun save(expected: ServerSchedule, patch: JsonObject, synchronizeEnabled: Boolean): ServerSchedule {
        if (!mutex.tryLock()) throw ScheduleException("A schedule save is already in progress. No additional write was sent.")
        var verifiedEnabled: Boolean? = null
        var robotProgress = "Robot enablement was not changed."
        try {
            if (!synchronizeEnabled && "enabled" in patch) {
                throw ScheduleException("Enablement must use synchronization. No writes were sent.")
            }
            var current = readCurrent(expected.id, synchronizeEnabled)
            if (current == null || snapshot(current) != snapshot(expected)) {
                throw ScheduleException("This schedule changed or was deleted. Refresh before editing it again. No writes were sent.")
            }
            if (synchronizeEnabled) {
                val enabled = (patch["enabled"] as? JsonPrimitive)?.booleanOrNull
                    ?: throw ScheduleException("Choose an enabled state. No writes were sent.")
                val settings = current.settings
                // The method is not evidence of a native timer. Only an explicit valid name is.
                if (settings?.containsKey("name") == true) {
                    val rawName = settings["name"] as? JsonPrimitive
                    val name = rawName?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                        ?: throw ScheduleException("The native timer reference is invalid. No writes were sent.")
                    val desired = if (enabled) "On" else "Off"
                    val initial = nativeTimerState(request { readTimers() }, name)
                    if (initial !in listOf("On", "Off")) {
                        throw ScheduleException("Native timer is ${initial.lowercase()}. No client write was sent; refresh and check More details. Timers are not recreated automatically.")
                    }
                    if (initial != desired) {
                        // A concurrent server edit must stop us before changing the robot.
                        current = readCurrent(expected.id, synchronizeEnabled)
                        if (current == null || !matchesExpected(current, expected, initial == "On")) {
                            throw ScheduleException("Schedule conflict before robot update. No writes were sent; refresh.")
                        }
                        robotProgress = "A robot update was attempted; its current state is unconfirmed."
                        // A lost MQTT reply isn't proof of failure. Read back once, never resend.
                        try { request { writeTimer(name, enabled) } }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Read-back determines whether the state was applied. */ }
                        val verified = try { nativeTimerState(request { readTimers() }, name) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            throw ScheduleException("$robotProgress No HTTP job update was sent; server-side reconciliation may still have changed enablement. Refresh before retrying; no rollback was attempted.")
                        }
                        if (verified != desired) {
                            throw ScheduleException("Robot did not confirm the requested state. No HTTP job update was sent; server-side reconciliation may still have changed enablement. Refresh before retrying; no rollback was attempted.")
                        }
                    }
                    verifiedEnabled = enabled
                    robotProgress = "Robot timer was verified ${desired.lowercase()}."
                }
            }

            // Recheck even when no native write was needed. Never overwrite a concurrent edit.
            current = readCurrent(expected.id, synchronizeEnabled)
            if (current == null || !matchesExpected(current, expected, verifiedEnabled)) {
                throw ScheduleException("$robotProgress Server job changed or was deleted; it was not overwritten. Refresh. No rollback was attempted.")
            }
            val baseline = current
            val target = ServerSchedule(JsonObject(baseline.raw + patch))
            if (snapshot(target) == snapshot(baseline)) return baseline
            var writeFailed = false
            try { request { writeJob(baseline, patch) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { writeFailed = true }
            // HTTP success alone isn't confirmation; a failed response can also hide a saved write.
            val confirmed = try { readCurrent(expected.id, synchronizeEnabled) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                throw ScheduleException("$robotProgress Server save is unconfirmed because read-back failed. Refresh before retrying; no writes were repeated or rolled back.")
            }
            if (confirmed != null && snapshot(confirmed) == snapshot(target)) return confirmed
            val reason = when {
                confirmed == null -> "The server job is missing."
                snapshot(confirmed) == snapshot(baseline) -> if (writeFailed) "Server save failed; the job is unchanged." else "Server did not retain the requested changes."
                else -> "Server read-back conflicts with the requested changes."
            }
            throw ScheduleException("$robotProgress $reason The combined save is incomplete. Refresh before retrying; no rollback was attempted.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScheduleException) {
            if (robotProgress != "Robot enablement was not changed." && !e.message.orEmpty().startsWith(robotProgress)) {
                throw ScheduleException("$robotProgress ${e.message} No rollback was attempted.")
            }
            throw e
        } catch (_: Exception) {
            throw ScheduleException("$robotProgress Could not read the current schedule or timer state. No HTTP job update was sent; server-side reconciliation may still have changed enablement. Refresh before retrying.")
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun readCurrent(id: String, validateReference: Boolean): ServerSchedule? {
        val jobs = request { readJobs() }
        val matches = jobs.filter { it.id == id }
        if (matches.size > 1) throw ScheduleException("Ambiguous server job ID. Refresh; no further writes were sent.")
        val job = matches.singleOrNull() ?: return null
        if (validateReference) {
            val entries = job.param?.get("params") as? JsonArray
            val references = entries.orEmpty().filterIsInstance<JsonObject>().filter { "name" in it }
            if (references.isNotEmpty() && job.settings == null) {
                throw ScheduleException("Ambiguous native timer references in the schedule. No further writes were sent.")
            }
            val name = job.nativeName
            if (name != null && jobs.count { other ->
                val params = other.param?.get("params") as? JsonArray
                params.orEmpty().filterIsInstance<JsonObject>().any { (it["name"] as? JsonPrimitive)?.contentOrNull == name }
            } > 1) {
                throw ScheduleException("Multiple server jobs share this native timer. No further writes were sent; resolve the conflicting references first.")
            }
        }
        return job
    }

    /** Session timeouts cancel an inner await, not this save. Preserve actual caller cancellation. */
    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw ScheduleException("Connection closed or request timed out.")
    }

    // #213 may reconcile enabled during native read-back. Accept only that exact, verified
    // transition; edits to any other job field remain conflicts.
    private fun matchesExpected(current: ServerSchedule, expected: ServerSchedule, reconciled: Boolean?): Boolean {
        if (snapshot(current) == snapshot(expected)) return true
        return reconciled != null && current.enabled == reconciled &&
            snapshot(current).filterKeys { it != "enabled" } == snapshot(expected).filterKeys { it != "enabled" }
    }

    private fun snapshot(job: ServerSchedule): JsonObject = JsonObject(job.snapshot().toMutableMap().apply {
        // The server can serialize param as either an object or a JSON string.
        job.param?.let { put("param", it) }
    })
}

/** Captured native RPC shape; timer name and HTTP job ID must never be interchanged. */
fun nativeTimerUpdateParams(name: String, enabled: Boolean): List<JsonElement> =
    listOf(JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(if (enabled) "on" else "off"))))

