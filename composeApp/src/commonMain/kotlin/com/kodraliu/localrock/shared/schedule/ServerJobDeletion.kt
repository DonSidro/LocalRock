package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Verify each side of a deletion; uncertain writes are read back, never repeated or rolled back. */
class ServerJobDeletion(
    private val readJobs: suspend () -> List<ServerSchedule>,
    private val readTimers: suspend () -> JsonElement?,
    private val deleteTimer: suspend (String) -> Unit,
    private val deleteJob: suspend (String) -> Unit,
) {
    suspend fun delete(expected: ServerSchedule) {
        var progress = "No native deletion was sent."
        try {
            val jobs = request { readJobs() }
            val current = jobs.filter { it.id == expected.id }.singleOrNull()
            if (current == null || normalized(current) != normalized(expected)) {
                throw ScheduleException("Schedule changed or was deleted. Refresh before deleting it.")
            }
            val entries = current.param?.get("params") as? JsonArray
            val references = entries.orEmpty().filterIsInstance<JsonObject>().filter { "name" in it }
            if (references.isNotEmpty()) {
                val rawName = current.settings?.get("name") as? JsonPrimitive
                val name = rawName?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
                    ?: throw ScheduleException("Missing or ambiguous native timer reference.")
                if (jobs.count { job ->
                    (job.param?.get("params") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
                        .any { (it["name"] as? JsonPrimitive)?.contentOrNull == name }
                } != 1) throw ScheduleException("Multiple jobs share this native timer; resolve the references first.")
                val state = nativeTimerState(request { readTimers() }, name)
                if (state !in listOf("On", "Off")) throw ScheduleException("Native timer is ${state.lowercase()}; deletion stopped.")
                val fresh = request { readJobs() }.filter { it.id == expected.id }.singleOrNull()
                if (fresh == null || normalized(fresh) != normalized(expected)) throw ScheduleException("Schedule changed before native deletion.")
                progress = "Native deletion was attempted; its state is unconfirmed."
                try { request { deleteTimer(name) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Inspect the result, without retrying the write. */ }
                if (nativeTimerState(request { readTimers() }, name) != "Missing") {
                    throw ScheduleException("Native timer deletion was not confirmed; the server job was retained.")
                }
                progress = "Native timer deletion was confirmed."
            }
            val fresh = request { readJobs() }.filter { it.id == expected.id }.singleOrNull()
            if (fresh == null || normalized(fresh) != normalized(expected)) {
                throw ScheduleException("Server job changed or disappeared; no server delete was sent.")
            }
            progress += " Server deletion was attempted."
            try { request { deleteJob(expected.id) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A lost acknowledgement may still mean success. */ }
            if (request { readJobs() }.any { it.id == expected.id }) {
                throw ScheduleException("Server job deletion was not confirmed.")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            throw ScheduleException("$progress ${e.message ?: "Could not verify the current state."} Refresh before retrying; no rollback was attempted.")
        }
    }

    private fun normalized(job: ServerSchedule) = JsonObject(job.snapshot() + listOfNotNull(job.param?.let { "param" to it }).toMap())
    private suspend fun <T> request(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw ScheduleException("Connection closed or request timed out.")
    }
}
