package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.schedule.ServerSchedule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The cloud's own record of a schedule, kept by `POST/PUT/DELETE /user/devices/{duid}/jobs`.
 *
 * A server_timer schedule lives on the robot, but the official app also writes this record for every
 * one, right after saving it to the robot, and treats a robot schedule with no record as stray: it
 * deletes it the next time its schedule screen opens. The record is also what `get_timer` answers
 * from, so a schedule without one never shows in this app's own list either. Both confirmed live.
 */
interface ScheduleMirror {
    suspend fun list(duid: String): List<ServerSchedule>
    /** Adds the record and returns the id the cloud gave it. */
    suspend fun create(duid: String, job: JsonObject): String
    suspend fun update(duid: String, jobId: String, job: JsonObject)
    suspend fun delete(duid: String, jobId: String)
}

private val EVERY_DAY = setOf(0, 1, 2, 3, 4, 5, 6)

/**
 * The cron the cloud record carries: `m h d mon ?` for a one-time schedule and `m h ? * days` for a
 * repeating one. The robot's own cron differs only in using `*` where the record has `?`.
 */
internal fun mirrorCron(minute: Int, hour: Int, weekdays: Set<Int>, date: TimerDate?): String {
    if (date != null) return "$minute $hour ${date.day} ${date.month} ?"
    return "$minute $hour ? * ${weekdays.ifEmpty { EVERY_DAY }.sorted().joinToString(",")}"
}

/**
 * The body of a cloud record, as the official app sends it. [robotId] goes in the settings as `name`,
 * which is how the record points at the robot's own schedule. A create carries an empty `id` for the
 * cloud to fill in; an update has none.
 */
internal fun mirrorJobBody(
    cron: String,
    enabled: Boolean,
    repeated: Boolean,
    settings: TimerSettings,
    robotId: String,
    forCreate: Boolean,
): JsonObject {
    val entry = JsonObject(settings.toActionObject() + ("name" to JsonPrimitive(robotId)))
    val param = JsonObject(
        mapOf(
            "id" to JsonPrimitive(1),
            "method" to JsonPrimitive("server_scheduled_start"),
            "params" to JsonArray(listOf(entry)),
        ),
    )
    val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
    if (forCreate) fields["id"] = JsonPrimitive("")
    fields["cron"] = JsonPrimitive(cron)
    fields["repeated"] = JsonPrimitive(repeated)
    fields["enabled"] = JsonPrimitive(enabled)
    fields["param"] = param
    return JsonObject(fields)
}
