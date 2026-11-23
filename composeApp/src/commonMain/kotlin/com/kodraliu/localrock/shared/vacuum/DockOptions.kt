package com.kodraliu.localrock.shared.vacuum

/** One value a dock setting can have: the code the robot uses, and what to call it. */
data class DockChoice(val code: Int, val label: String)

// Official plugin: docks with high-temperature water (normalizeHotWashMode), by status dock_type.
private val highTempWashDockTypes = setOf(29, 31, 40, 41, 42, 43, 46)

// Plugin DockConfigs entries merged with carouselSelfClean (p223 L281893-282680); all are the no-plumbing variants.
private val traySelfCleanDockTypes = setOf(14, 16, 17, 20, 23, 26, 27, 29, 31, 33, 34, 37, 38, 41, 43, 44, 46)

object DockOptions {
    /** Dock.isCleanCarouselSelfCleanSupported: a listed dock type without the plumbing module (dss & 3 == 2). */
    fun traySelfCleanSupported(dockType: Int?, dss: Int?): Boolean =
        dockType in traySelfCleanDockTypes && (dss ?: 0) and 3 != 2

    /** RSM.cleanFluidStatus (dss bits 10-11); the official cleaning fluid rows stay hidden while it is 0. */
    fun cleanFluidReported(dss: Int?): Boolean = dss != null && (dss shr 10) and 3 != 0

    /** The Mop Washing Temperature choices the official app offers on [dockType]. */
    fun washWaterTemperature(dockType: Int?): List<DockChoice> = if (dockType in highTempWashDockTypes) {
        listOf(DockChoice(0, "Room Temperature"), DockChoice(2, "High Temperature"))
    } else {
        listOf(DockChoice(0, "Room Temperature"), DockChoice(1, "Warm Water"), DockChoice(2, "Hot Water"))
    }

    /** The reported temperature as the official app reads it: a high-temperature dock treats 1 as 2. */
    fun washWaterTemperatureCode(raw: Int?, dockType: Int?): Int? =
        if (raw == 1 && dockType in highTempWashDockTypes) 2 else raw

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
