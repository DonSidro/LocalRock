package com.kodraliu.localrock.shared.schedule

import com.kodraliu.localrock.shared.vacuum.*
import kotlinx.serialization.json.*

enum class ScheduleRepeat(val label: String, val days: Set<Int>?) {
    ONCE("Once", (0..6).toSet()),
    EVERY_DAY("Every day", (0..6).toSet()),
    WEEKDAYS("Weekdays", (1..5).toSet()),
    WEEKENDS("Weekends", setOf(0, 6)),
    CUSTOM("Custom", null);

    companion object {
        fun from(repeated: Boolean, days: Set<Int>): ScheduleRepeat = when {
            !repeated -> ONCE
            days == EVERY_DAY.days -> EVERY_DAY
            days == WEEKDAYS.days -> WEEKDAYS
            days == WEEKENDS.days -> WEEKENDS
            else -> CUSTOM
        }
    }
}

val scheduleDayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

fun scheduleRepeatLabel(repeated: Boolean, days: Set<Int>): String {
    val preset = ScheduleRepeat.from(repeated, days)
    return if (preset != ScheduleRepeat.CUSTOM) preset.label
        else days.sortedBy { (it + 6) % 7 }.joinToString(", ") { scheduleDayNames[it] }
}

enum class ScheduleCleanMode(val label: String) {
    VACUUM("Vacuum"), VAC_MOP("Vac & Mop"), MOP("Mop"), CUSTOM("Custom");

    /** These are the same motor codes used by LocalRock's existing cleaning controls. */
    fun settings(): Map<String, JsonElement> {
        val fan = when (this) {
            MOP -> VacuumFanPower.OFF
            CUSTOM -> VacuumFanPower.CUSTOM
            else -> VacuumFanPower.BALANCED
        }
        val water = when (this) {
            VACUUM -> WaterBoxMode.OFF
            CUSTOM -> WaterBoxMode.CUSTOM
            else -> WaterBoxMode.MEDIUM
        }
        val mop = if (this == CUSTOM) MopRoute.CUSTOM else MopRoute.STANDARD
        return mapOf("fan_power" to JsonPrimitive(fan), "water_box_mode" to JsonPrimitive(water), "mop_mode" to JsonPrimitive(mop))
    }

    companion object {
        fun from(settings: JsonObject?): ScheduleCleanMode? {
            fun value(key: String) = (settings?.get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            val fan = value("fan_power")
            val water = WaterBoxMode.normalize(value("water_box_mode"))
            val mop = value("mop_mode")
            if (isCustomizeModeSet(fan, water, mop)) return CUSTOM
            val suction = fan in listOf(101, 102, 103, 104)
            val wet = water in listOf(201, 202, 203)
            return when {
                suction && water == WaterBoxMode.OFF -> VACUUM
                fan == VacuumFanPower.OFF && wet -> MOP
                suction && wet -> VAC_MOP
                else -> null // Preserve unknown/default/model-specific values without inventing a mode.
            }
        }
    }
}
