package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.schedule.ScheduleException
import com.kodraliu.localrock.shared.schedule.ServerSchedule
import kotlinx.serialization.json.*
import kotlinx.datetime.LocalDate

/** Unsupported jobs stay visible rather than becoming a misleading empty schedule list. */
internal fun ServerSchedule.toCleanTimer(): CleanTimer {
    val weekly = weekly
    val parts = cron.trim().split(Regex("\\s+"))
    val dated = if (parts.size == 5 && parts[4] in listOf("?", "*")) {
        val day = parts[2].toIntOrNull()
        val month = parts[3].toIntOrNull()
        if (day != null && month != null && runCatching { LocalDate(2000, month, day) }.isSuccess) TimerDate(month, day) else null
    } else null
    val h = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..23 }
    val m = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..59 }
    val supported = (weekly != null || dated != null) && h != null && m != null && settings != null &&
        (param?.get("method") as? JsonPrimitive)?.contentOrNull == "server_scheduled_start"
    return CleanTimer(id, enabled, h ?: 0, m ?: 0, weekly?.days ?: emptySet(),
        parseTimerSettings(settings), dated, this, supported)
}

/** Patch only user edits. Native identity, room order and unknown firmware fields survive. */
internal fun serverJobEditPatch(
    timer: CleanTimer,
    hour: Int,
    minute: Int,
    weekdays: Set<Int>,
    settings: TimerSettings,
    once: Boolean,
    date: TimerDate?,
    settingsChanged: Boolean,
    timingChanged: Boolean,
): JsonObject {
    val job = timer.serverJob ?: throw ScheduleException("This is not a server job")
    if (!timer.editable) throw ScheduleException("This schedule format is read-only")
    return buildJsonObject {
        if (timingChanged) {
            put("cron", mirrorCron(minute, hour, if (once) emptySet() else weekdays, date))
            put("repeated", !once)
        }
        if (settingsChanged) {
            val before = timer.settings.toActionObject()
            val after = settings.toActionObject()
            val edited = job.settings!!.toMutableMap()
            // Only the dialog's cleaning controls can change these fields. Rooms/map/name stay intact.
            for (key in listOf("fan_power", "water_box_mode", "mop_mode", "repeat")) {
                if (before[key] != after[key]) {
                    val value = after[key]
                    if (value == null) edited.remove(key) else edited[key] = value
                }
            }
            if (JsonObject(edited) != job.settings) {
                put("param", JsonObject(job.param!! + ("params" to JsonArray(listOf(JsonObject(edited))))))
            }
        }
    }
}
