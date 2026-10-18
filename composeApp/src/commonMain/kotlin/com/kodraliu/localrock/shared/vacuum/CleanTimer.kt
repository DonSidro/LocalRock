package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class CleanTimer(
    val id: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int>, // 0=Sun, 1=Mon … 6=Sat
    val settings: TimerSettings = TimerSettings(),
)

/**
 * What a schedule does when it runs. A null value leaves that setting to whatever the robot has at
 * the time, which is all a schedule without options ever did.
 *
 * These travel in the action object of set_timer / get_timer: `["start_clean", {"fan_power": 102,
 * "water_box_mode": 200, ...}]`. The official app's schedules carry the same fields. A robot or
 * server that does not know them ignores the extra keys, and a client that does not send them leaves
 * a schedule's stored options alone.
 */
data class TimerSettings(
    val fanPower: Int? = null,
    val waterBoxMode: Int? = null,
    val mopMode: Int? = null,
    val repeat: Int? = null,
    // Carried by server_timer schedules, never set by this app's own UI yet: which rooms to
    // clean ("0" for the whole home), whether mopping is on, and which saved map to use.
    val segments: String? = null,
    val cleanMop: Int? = null,
    val mapIndex: Int? = null,
)

/** The cleaning choice a schedule offers, as the official app words it. */
enum class TimerKind { CURRENT, VACUUM, VAC_AND_MOP, MOP }

val TimerSettings.kind: TimerKind
    get() = when {
        waterBoxMode == WaterBoxMode.OFF -> TimerKind.VACUUM
        fanPower == VacuumFanPower.OFF -> TimerKind.MOP
        waterBoxMode != null -> TimerKind.VAC_AND_MOP
        else -> TimerKind.CURRENT
    }

/**
 * Settings for [kind] from the individual choices. Mopping without a water level would not mop, so
 * it falls back to medium; vacuum only drops water and route, mop only drops suction.
 */
fun timerSettingsFor(
    kind: TimerKind,
    fanPower: Int?,
    waterBoxMode: Int?,
    mopMode: Int?,
    repeat: Int?,
): TimerSettings = when (kind) {
    TimerKind.CURRENT -> TimerSettings(repeat = repeat)
    TimerKind.VACUUM -> TimerSettings(fanPower, WaterBoxMode.OFF, null, repeat)
    TimerKind.VAC_AND_MOP -> TimerSettings(fanPower, waterBoxMode ?: WaterBoxMode.MEDIUM, mopMode, repeat)
    TimerKind.MOP -> TimerSettings(VacuumFanPower.OFF, waterBoxMode ?: WaterBoxMode.MEDIUM, mopMode, repeat)
}

/**
 * The action object for set_timer. It is `{}` when nothing is chosen, which is the wire format this
 * app always used. An option that is not chosen is left out of the object, never sent as null.
 */
internal fun TimerSettings.toActionObject(): JsonObject {
    val fields = LinkedHashMap<String, JsonElement>()
    fanPower?.let { fields["fan_power"] = JsonPrimitive(it) }
    waterBoxMode?.let { fields["water_box_mode"] = JsonPrimitive(it) }
    mopMode?.let { fields["mop_mode"] = JsonPrimitive(it) }
    repeat?.let { fields["repeat"] = JsonPrimitive(it) }
    segments?.let { fields["segments"] = JsonPrimitive(it) }
    cleanMop?.let { fields["clean_mop"] = JsonPrimitive(it) }
    mapIndex?.let { fields["map_index"] = JsonPrimitive(it) }
    return JsonObject(fields)
}

/** The settings in a get_timer action object; anything missing or not a number is left null. */
internal fun parseTimerSettings(action: JsonElement?): TimerSettings {
    val obj = action as? JsonObject ?: return TimerSettings()
    fun int(key: String) = runCatching { obj[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()
    fun str(key: String) = runCatching { obj[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    return TimerSettings(
        fanPower = int("fan_power"),
        waterBoxMode = int("water_box_mode"),
        mopMode = int("mop_mode"),
        repeat = int("repeat"),
        segments = str("segments"),
        cleanMop = int("clean_mop"),
        mapIndex = int("map_index"),
    )
}

private val FAN_POWER_LABELS = mapOf(
    VacuumFanPower.QUIET to "Quiet",
    VacuumFanPower.BALANCED to "Balanced",
    VacuumFanPower.TURBO to "Turbo",
    VacuumFanPower.MAX to "Max",
    108 to "Max+",
    VacuumFanPower.SMART to "Smart",
)
private val WATER_LABELS = mapOf(
    WaterBoxMode.LOW to "Low",
    WaterBoxMode.MEDIUM to "Medium",
    WaterBoxMode.HIGH to "High",
    WaterBoxMode.SMART to "Smart",
)
private val ROUTE_LABELS = mapOf(
    MopRoute.STANDARD to "Standard",
    MopRoute.DEEP to "Deep",
    MopRoute.DEEP_PLUS to "Deep+",
    MopRoute.FAST to "Fast",
    MopRoute.SMART to "Smart",
)

/** One line for a schedule row, or null when the schedule just uses the robot's current settings. */
fun TimerSettings.summary(): String? {
    val parts = mutableListOf<String>()
    when (kind) {
        TimerKind.VACUUM -> parts += "Vacuum"
        TimerKind.VAC_AND_MOP -> parts += "Vacuum and mop"
        TimerKind.MOP -> parts += "Mop"
        TimerKind.CURRENT -> Unit
    }
    // A code this app has no name for is shown as its number rather than dropped.
    if (kind != TimerKind.MOP) {
        fanPower?.let { parts += FAN_POWER_LABELS[it]?.let { name -> "$name suction" } ?: "Suction $it" }
    }
    if (kind == TimerKind.VAC_AND_MOP || kind == TimerKind.MOP) {
        waterBoxMode?.let { code ->
            val water = WaterBoxMode.normalize(code) ?: code
            parts += WATER_LABELS[water]?.let { name -> "$name water" } ?: "Water $water"
        }
        mopMode?.let { parts += ROUTE_LABELS[it]?.let { name -> "$name route" } ?: "Route $it" }
    }
    repeat?.takeIf { it > 1 }?.let { parts += "$it passes" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
