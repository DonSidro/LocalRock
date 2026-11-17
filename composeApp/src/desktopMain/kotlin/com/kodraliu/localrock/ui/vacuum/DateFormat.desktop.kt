package com.kodraliu.localrock.ui.vacuum

import java.text.DateFormat
import java.util.Calendar
import java.util.Date

actual fun formatCleanRecordTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "Unknown"
    val date = Date(epochSeconds * 1000L)
    val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)

    val then = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sameDay(then, today) -> "Today, ${timeFmt.format(date)}"
        sameDay(then, yesterday) -> "Yesterday, ${timeFmt.format(date)}"
        else -> {
            val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM)
            "${dateFmt.format(date)}, ${timeFmt.format(date)}"
        }
    }
}
