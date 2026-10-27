package com.kodraliu.localrock.shared.schedule

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Complete a native creation with one HTTP POST, then read back even if its response was lost. */
class ServerJobCreation(
    private val readTimers: suspend () -> JsonElement?,
    private val createJob: suspend (JsonObject) -> String,
    private val readJobs: suspend () -> List<ServerSchedule>,
) {
    suspend fun confirm(name: String, body: JsonObject): ServerSchedule {
        try {
            if (nativeTimerState(request { readTimers() }, name) != "On") {
                throw ScheduleException("Native timer creation could not be verified. Server job was not created.")
            }
            var returnedId: String? = null
            try { returnedId = request { createJob(body) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Lost response is not proof of failure. Do not repeat POST. */ }
            val matches = request { readJobs() }.filter { it.nativeName == name }
            val saved = matches.singleOrNull()
            if (saved != null && (returnedId == null || returnedId == saved.id) &&
                body.filterKeys { it != "id" }.all { (key, value) ->
                    (if (key == "param") saved.param else saved.raw[key]) == value
                }) return saved
            throw ScheduleException("Matching server job is missing, ambiguous or different.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            throw ScheduleException("Native creation was attempted; combined save is unconfirmed. ${e.message.orEmpty()} No timer was deleted and no write was repeated. Refresh before retrying.")
        }
    }

    private suspend fun <T> request(block: suspend () -> T): T = try { block() }
    catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw ScheduleException("Connection closed or request timed out.")
    }
}
