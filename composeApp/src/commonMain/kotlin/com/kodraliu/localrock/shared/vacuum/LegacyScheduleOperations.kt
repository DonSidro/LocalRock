package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.device.DeviceApiException
import com.kodraliu.localrock.shared.schedule.ScheduleException
import kotlinx.coroutines.CancellationException

/** Only an unavailable jobs collection permits reading legacy schedules. */
internal suspend fun <T> loadScheduleList(
    readJobs: (suspend () -> List<T>)?,
    readLegacy: suspend () -> List<T>,
): List<T> {
    if (readJobs != null) {
        try { return readJobs() }
        catch (e: DeviceApiException) {
            if (e.responseCode != 404 && e.responseCode != 405) throw e
        }
    }
    return readLegacy()
}

/** Used only for legacy delete-and-add edits, never for HTTP server-job edits. */
internal suspend fun replaceLegacySchedule(
    replace: suspend () -> Unit,
    restore: suspend () -> Unit,
) {
    try { replace() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
        try { restore() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (rollback: Exception) {
            throw ScheduleException("The schedule edit failed (${e.message}); restoring the old schedule also failed (${rollback.message}). Refresh to check its state.")
        }
        throw ScheduleException("The schedule edit failed (${e.message}). The old schedule was restored.")
    }
}
