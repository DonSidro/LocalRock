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
 * get/set/upd/del_timer. Confirmed live against a Roborock Qrevo: the robot stores this kind
 * of schedule itself and fires it autonomously from its own clock, no app, cloud, or server
 * involvement needed once it is saved. Firmware that only understands this shape rejects the
 * legacy set_timer with `-10005 "timer type error"` (see LocalRock #28), not because the
 * settings are wrong, but because the envelope itself is unrecognised.
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
