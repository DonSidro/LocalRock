package com.kodraliu.localrock.shared.vacuum

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

private fun Int.two(): String = toString().padStart(2, '0')

/** The official History row and run title: "11/13  11:23" in the phone's zone. */
fun historyStamp(epochSeconds: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
    return "${t.monthNumber.two()}/${t.dayOfMonth.two()}  ${t.hour.two()}:${t.minute.two()}"
}

/** A timeline node's clock time, "13:53", as the official app prints it. */
fun timelineClock(epochSeconds: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(zone)
    return "${t.hour.two()}:${t.minute.two()}"
}

/** Total Time in whole minutes, as the official header shows it ("112" min). */
fun totalMinutes(seconds: Long): Long = (seconds / 60.0).roundToInt().toLong()

/** Area value and unit for the big-number cells: "1012" and "m²", "29.9k" and "m²". */
fun areaParts(label: String): Pair<String, String> {
    val cut = label.lastIndexOf(' ')
    return if (cut < 0) label to "" else label.substring(0, cut) to label.substring(cut + 1)
}

/** The Working Times help text; cruise runs only appear when the robot reports some. */
fun workingTimesHelp(cleanTimes: Int, patrolTimes: Int): String =
    if (patrolTimes > 0) "Working times $cleanTimes, cruise times $patrolTimes" else "Working times $cleanTimes"

/** The header count: the official app adds cruise runs to the clean count (Curv: 2 + 4 = "6 time(s)"). */
fun workingTimes(summary: CleanSummary): Int = summary.totalCount + summary.patrolCount

/** A History row's second line, "start type | duration": "App | 112 min", "Search for the Pet | 19 min". */
fun historyRowSubtitle(record: CleanRecord): String {
    val what = if (record.isCruise) record.cruiseTitle else startTypeLabel(record.startType) ?: cleanTypeLabel(record.cleanType)
    return listOfNotNull(what, cleanDurationLabel(record.durationSeconds)).joinToString(" | ")
}

/** The official result line of a cruise or pet-finding run. */
fun cruiseResultLabel(record: CleanRecord): String {
    val pet = record.cleanType == CLEAN_TYPE_PET_PATROL
    val ok = record.complete == 1
    return when {
        pet && ok -> "Pet found"
        pet -> "Pet found failed"
        ok -> "Cruise completed"
        else -> "Cruise failed"
    }
}
