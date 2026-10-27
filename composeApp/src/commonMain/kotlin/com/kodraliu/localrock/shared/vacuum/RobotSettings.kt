package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

data class RobotSettings(
    val volume: Int = 50,
    val childLock: Boolean = false,
    val ledEnabled: Boolean = true,
    val carpetMode: Int = 1, // 0=avoid, 1=standard, 2=boost
)

/** The levels the volume picker offers, as change_sound_volume values (0-100). */
val VolumeLevels = listOf(0 to "Off", 30 to "Low", 60 to "Medium", 100 to "High")

/**
 * The picker level for a volume the robot reports. Only 0 is Off: a robot set to 10 elsewhere still
 * speaks, so it is Low, not the nearer Off.
 */
fun volumeLevelIndex(volume: Int): Int =
    if (volume <= 0) 0 else (1 until VolumeLevels.size).minBy { abs(VolumeLevels[it].first - volume) }

/** The level's name, with the robot's own value when it is between levels, e.g. "Low (10%)". */
fun volumeLabel(volume: Int): String {
    val (level, name) = VolumeLevels[volumeLevelIndex(volume)]
    return if (volume == level) name else "$name ($volume%)"
}

data class DockSettings(
    val washMode: Int? = null,
    val smartWash: SmartWashSettings? = null,
    val autoEmptyMode: Int? = null,
)

data class SmartWashSettings(val mode: Int, val intervalSeconds: Int) {
    fun withFrequency(mode: Int, intervalMinutes: Int?): SmartWashSettings {
        require(mode in 0..2) { "Unsupported mop washing frequency" }
        require((mode == 0) == (intervalMinutes != null)) { "Choose an interval for time-based washing" }
        if (intervalMinutes != null) {
            require(intervalMinutes in 10..50 && intervalMinutes % 5 == 0) { "Invalid mop washing interval" }
        }
        return copy(mode = mode, intervalSeconds = intervalMinutes?.times(60) ?: intervalSeconds)
    }
}

internal fun parseSmartWashSettings(result: JsonElement?): SmartWashSettings? {
    val params = result as? JsonObject ?: return null
    val mode = (params["smart_wash"] as? JsonPrimitive)?.intOrNull ?: return null
    val interval = (params["wash_interval"] as? JsonPrimitive)?.intOrNull ?: return null
    return interval.takeIf { it > 0 }?.let { SmartWashSettings(mode, it) }
}

internal fun encodeSmartWashParams(settings: SmartWashSettings): JsonObject = JsonObject(mapOf(
    "smart_wash" to JsonPrimitive(settings.mode),
    "wash_interval" to JsonPrimitive(settings.intervalSeconds),
))

fun washFrequencyLabel(settings: SmartWashSettings?): String = when (settings?.mode) {
    null -> "Unavailable"
    0 -> if (settings.intervalSeconds % 60 == 0) "${settings.intervalSeconds / 60} min" else "${settings.intervalSeconds} sec"
    1 -> "After each room"
    2 -> "Smart"
    else -> "Unknown (${settings.mode})"
}

data class CleanSummary(
    val totalTimeSec: Long,
    val totalAreaMm2: Long,
    val totalCount: Int,
)

data class DndSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
)

data class FloorMap(val name: String, val mapFlag: Int)
