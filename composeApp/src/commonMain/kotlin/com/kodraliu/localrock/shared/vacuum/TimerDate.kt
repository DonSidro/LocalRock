package com.kodraliu.localrock.shared.vacuum

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The calendar day a one-time schedule runs on. The official app's "Once" is a cron with the day of
 * the month and the month pinned (`15 23 18 10 *`) and no year, captured live.
 */
data class TimerDate(val month: Int, val day: Int)

/**
 * The next day [hour]:[minute] comes round, counting from [nowEpochSeconds] in [zone]: today while
 * that minute is still ahead, tomorrow otherwise.
 */
fun nextTimerDate(nowEpochSeconds: Long, zone: TimeZone, hour: Int, minute: Int): TimerDate {
    val now = Instant.fromEpochSeconds(nowEpochSeconds).toLocalDateTime(zone)
    val ahead = hour * 60 + minute > now.hour * 60 + now.minute
    val day = if (ahead) now.date else now.date.plus(1, DateTimeUnit.DAY)
    return TimerDate(day.monthNumber, day.dayOfMonth)
}
