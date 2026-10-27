package com.kodraliu.localrock.shared.vacuum

/** One value a dock setting can have: the code the robot uses, and what to call it. */
data class DockChoice(val code: Int, val label: String)

object DockOptions {
    // Only 2 = Hot water is confirmed on a robot; the other codes follow the official app's order.
    val washWaterTemperature = listOf(
        DockChoice(0, "Room temperature"),
        DockChoice(1, "Warm water"),
        DockChoice(2, "Hot water"),
    )

    /** app_set_dryer_setting's dry_time, in seconds; a robot reporting 7200 showed "2 h". */
    val dryTime = listOf(1, 2, 3, 4).map { DockChoice(it * 3600, "$it h") }
}

/** The label for [code], or "Other (code)" so a value this app has no name for is still shown. */
fun List<DockChoice>.labelFor(code: Int): String =
    firstOrNull { it.code == code }?.label ?: "Other ($code)"

/** How long a drying run lasts, from the robot's seconds: "2 h", "90 min". */
fun dryTimeLabel(seconds: Int): String = when {
    seconds % 3600 == 0 -> "${seconds / 3600} h"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "$seconds s"
}
