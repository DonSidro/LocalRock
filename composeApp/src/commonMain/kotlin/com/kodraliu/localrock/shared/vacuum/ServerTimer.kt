package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A newer, separate scheduling protocol some firmware speaks instead of (or alongside)
 * get/set/upd/del_timer. Native entries retain identity and settings; their presence alone does
 * not establish autonomous execution. Server-linked jobs require the server scheduler to dispatch
 * the due clean. Firmware may reject the legacy envelope with -10005 "timer type error".
 *
 * Which protocol a robot wants is not guesswork: the official app decides it from
 * `app_get_init_status`'s `result[0].local_info.featureset`, bit 0. See [usesServerTimer].
 */
data class ServerTimerEntry(
    val id: String,
    val enabled: Boolean,
    val mapIndex: Int,
)

/** One `[id, "on"/"off", map_index]` row from a get_server_timer result. */
internal fun parseServerTimerEntry(row: JsonElement): ServerTimerEntry? {
    val fields = row as? JsonArray ?: return null
    if (fields.size < 3) return null
    val id = fields[0].jsonPrimitive.contentOrNull ?: return null
    val status = fields[1].jsonPrimitive.contentOrNull
    if (status != "on" && status != "off") return null
    val mapIndex = fields[2].jsonPrimitive.contentOrNull?.toIntOrNull() ?: -1
    return ServerTimerEntry(id = id, enabled = status == "on", mapIndex = mapIndex)
}

/** The full get_server_timer result: every entry that parses, in the order the robot sent them. */
internal fun parseServerTimerList(result: JsonElement?): List<ServerTimerEntry> {
    val rows = result as? JsonArray ?: return emptyList()
    return rows.mapNotNull { parseServerTimerEntry(it) }
}

/**
 * The params object for one set_server_timer entry: `{"data": [[id, [cron, action]]], "need_retry": 1}`.
 * Unlike set_timer's bare array, this is an object, and the entry itself carries no on/off status,
 * a freshly saved schedule starts on; turning one off is a separate upd_server_timer call.
 */
internal fun serverTimerSetParams(id: String, cron: String, settings: TimerSettings): JsonObject {
    val entry = JsonArray(listOf(
        JsonPrimitive(id),
        JsonArray(listOf(
            JsonPrimitive(cron),
            JsonArray(listOf(JsonPrimitive("start_clean"), settings.toActionObject())),
        )),
    ))
    return JsonObject(mapOf(
        "data" to JsonArray(listOf(entry)),
        "need_retry" to JsonPrimitive(1),
    ))
}

/**
 * [this] with every field a set_server_timer entry carries. Confirmed live: the official app always
 * sends `repeat`, `water_box_mode`, `segments`, `mop_mode`, `fan_power`, `clean_mop` and `map_index`,
 * and a robot answered `-10005 "Param error"` to an entry with only the three mode codes.
 * The schedule screen always chooses the three modes, so the defaults here only cover an old
 * schedule that carried none.
 */
internal fun TimerSettings.completedForServerTimer(): TimerSettings = copy(
    fanPower = fanPower ?: VacuumFanPower.BALANCED,
    waterBoxMode = waterBoxMode ?: WaterBoxMode.MEDIUM,
    mopMode = mopMode ?: MopRoute.STANDARD,
    repeat = repeat ?: 1,
    segments = segments ?: "0",
    cleanMop = cleanMop ?: 1,
    mapIndex = mapIndex ?: -1,
)

/**
 * Whether a robot's refusal means "this is the other kind of timer", the one case worth retrying on
 * the other protocol. Both that and an ordinary bad-parameter refusal use code -10005; only the
 * first says "timer type error". Retrying a bad request on the legacy protocol would save it on the
 * server instead of the robot, so it would be run by the server rather than by the robot.
 */
internal fun isTimerTypeError(error: JsonObject): Boolean {
    val code = error["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
    val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return code == -10005 && message.contains("type", ignoreCase = true)
}

/**
 * Whether the robot's own account considers it a server_timer device, read the same way the
 * official app does: bit 0 of `app_get_init_status`'s `result[0].local_info.featureset`. `true`
 * means use get/set/upd/del_server_timer; `false` means legacy get/set/upd/del_timer. A robot
 * can still be more lenient than this flag promises (this project's own test robot accepted
 * legacy set_timer even with this bit set), so callers should still fall back on a `-10005`
 * rejection rather than treat this as the only signal.
 */
internal fun usesServerTimer(initStatusResult: JsonElement?): Boolean? {
    val entry = (initStatusResult as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val localInfo = entry["local_info"] as? JsonObject ?: return null
    val featureSet = localInfo["featureset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
    return (featureSet and 1) != 0
}
