package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

// How a feature whose RPCs do not fit the plain get/set pair is read and written.
sealed interface FeatureCodec {
    // A bit of get_status.switch_status, which plain get_status leaves out; only get_prop ["get_status"] carries it.
    data class StatusSwitchBit(val mask: Int) : FeatureCodec

    // The set command takes only the bare object; other shapes time out instead of being refused.
    data object BareObjectFirst : FeatureCodec

    // One bit of the camera_status word, written read-modify-write; [requiredBits] must be on to turn it on.
    // Turning it off also clears [clearsWhenOff], as the official app's updateCameraStatus does.
    data class CameraStatusBit(
        val bit: Int,
        val requiredBits: List<Int>,
        val blockedReason: String,
        val clearsWhenOff: List<Int> = emptyList(),
    ) : FeatureCodec

    // A [width]-bit field of the camera_status word at [shift], written read-modify-write.
    data class CameraStatusField(val shift: Int, val width: Int) : FeatureCodec

    // Ground material and furniture recognition behind one switch; [withRoomType] adds identify_room_status (bit 66).
    data class AiEnvironment(val withRoomType: Boolean = false) : FeatureCodec

    // The status field of app_get/set_dryer_setting: 0 manual, 1 auto, 2 scheduled.
    data object DryerStartMethod : FeatureCodec

    // The enable field of get/set_carpet_mode; the set sends the whole object back with only enable changed.
    data object CarpetModeEnable : FeatureCodec
}

private val GetStatusParams = JsonArray(listOf(JsonPrimitive("get_status")))

// Default time for Scheduled Drying when the robot holds none, as cron; the official app asks for one.
internal const val DEFAULT_DRY_CRON = "0 3 * * *"

private fun JsonElement?.single(): JsonElement? = if (this is JsonArray && size == 1) this[0] else this

private fun JsonElement?.intField(name: String): Int? = ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.intOrNull

/** Reads the feature's value from its get reply; the key is the field the generic writer would use. */
fun parseFeatureReply(def: BetaFeatureDef, result: JsonElement?): Pair<String?, Int>? = when (val c = def.codec) {
    is FeatureCodec.StatusSwitchBit -> result.single().intField("switch_status")?.let { "status" to if (it and c.mask != 0) 1 else 0 }
    is FeatureCodec.CameraStatusBit -> cameraWord(result)?.let { null to ((it shr c.bit) and 1) }
    is FeatureCodec.CameraStatusField -> cameraWord(result)?.let { null to ((it shr c.shift) and fieldMask(c.width)) }
    is FeatureCodec.AiEnvironment -> parseAiEnvironment(result)?.let { "status" to it }
    FeatureCodec.DryerStartMethod -> result.single().intField("status")?.let { "status" to it }
    FeatureCodec.CarpetModeEnable -> result.single().intField("enable")?.let { "enable" to it }
    else -> parseBetaReply(result)
}

internal fun cameraWord(result: JsonElement?): Int? = (result.single() as? JsonPrimitive)?.intOrNull

/** Why the switch cannot be turned on now (shown as the disabled row's tip), from the raw get reply. */
fun featureBlockedReason(def: BetaFeatureDef, raw: String?): String? {
    val c = def.codec as? FeatureCodec.CameraStatusBit ?: return null
    val word = runCatching { cameraWord(Json.parseToJsonElement(raw ?: return null)) }.getOrNull() ?: return null
    return c.blockedReason.takeIf { cameraBitBlocked(c, word) }
}

internal fun cameraBitBlocked(c: FeatureCodec.CameraStatusBit, word: Int): Boolean = c.requiredBits.any { (word shr it) and 1 == 0 }

/** The camera_status word with [bit] set to [on], every other bit kept. */
internal fun cameraWordWith(word: Int, bit: Int, on: Boolean): Int = if (on) word or (1 shl bit) else word and (1 shl bit).inv()

/** The word written for [c]: off also clears the bits that depend on it. */
internal fun cameraWordFor(c: FeatureCodec.CameraStatusBit, word: Int, on: Boolean): Int =
    if (on) cameraWordWith(word, c.bit, true) else (c.clearsWhenOff + c.bit).fold(word) { w, b -> cameraWordWith(w, b, false) }

private fun fieldMask(width: Int) = (1 shl width) - 1

/** The camera_status word with [c]'s field set to [value], every other bit kept. */
internal fun cameraWordWithField(c: FeatureCodec.CameraStatusField, word: Int, value: Int): Int {
    val mask = fieldMask(c.width) shl c.shift
    return (word and mask.inv()) or ((value shl c.shift) and mask)
}

internal const val GET_GROUND_MATERIAL = "get_identify_ground_material_status"
internal const val SET_GROUND_MATERIAL = "set_identify_ground_material_status"
internal const val GET_FURNITURE = "get_identify_furniture_status"
internal const val SET_FURNITURE = "set_identify_furniture_status"
internal const val IDENTIFY_ROOM_SETTING = "identify_room_status"
internal const val IDENTIFY_ROOM_BIT = 66

/** The AI Environment Recognition reads joined into one answer; [room] is null when it was not read. */
internal fun aiEnvironmentAnswer(ground: JsonElement?, furniture: JsonElement?, room: JsonElement?): JsonObject {
    val parts = mutableMapOf("ground" to (ground ?: JsonNull), "furniture" to (furniture ?: JsonNull))
    if (room != null) parts["room"] = room
    return JsonObject(parts)
}

/** On if any part reads 1, as the official app shows it; null unless both ground and furniture parsed. */
internal fun parseAiEnvironment(result: JsonElement?): Int? {
    val o = result as? JsonObject ?: return null
    val ground = o["ground"].single().intField("status") ?: return null
    val furniture = o["furniture"].single().intField("status") ?: return null
    val room = o["room"].single().intField("status")
    return if (ground == 1 || furniture == 1 || room == 1) 1 else 0
}

/** The robot reads behind [def], as method and setting name; a write refreshes every row sharing one. */
internal fun featureReads(def: BetaFeatureDef): Set<Pair<String, String?>> = when (val c = def.codec) {
    is FeatureCodec.AiEnvironment -> setOfNotNull(
        GET_GROUND_MATERIAL to null,
        GET_FURNITURE to null,
        ("app_get_robot_setting" to IDENTIFY_ROOM_SETTING).takeIf { c.withRoomType },
    )
    else -> setOf(def.getMethod to def.settingName)
}

/** Minimal app_set_dryer_setting body the official app sends: {on:{dry_time}, status, cron}. */
internal fun dryerStartMethodBody(reported: JsonObject?, status: Int, cron: String? = null): JsonObject {
    val dryTime = (reported?.get("on") as? JsonObject).intField("dry_time")
    val held = (reported?.get("cron") as? JsonPrimitive)?.contentOrNull.orEmpty()
    val sentCron = cron ?: if (status == 2 && held.isBlank()) DEFAULT_DRY_CRON else held
    val body = mutableMapOf<String, JsonElement>()
    if (dryTime != null) body["on"] = JsonObject(mapOf("dry_time" to JsonPrimitive(dryTime)))
    body["status"] = JsonPrimitive(status)
    body["cron"] = JsonPrimitive(sentCron)
    return JsonObject(body)
}

/** set_carpet_mode params: the object get_carpet_mode reported, kept as read, with only enable set to [enable]. */
internal fun carpetModeSetParams(reported: JsonObject, enable: Int): JsonArray =
    JsonArray(listOf(JsonObject(reported + ("enable" to JsonPrimitive(enable)))))

/** Param shapes to try for [def]'s set command, in order; the robot's -10007/-10005 moves on to the next. */
fun featureSetCandidates(def: BetaFeatureDef, key: String?, value: Int): List<JsonElement> {
    val v = JsonPrimitive(value)
    def.settingName?.let { return listOf(robotSettingWriteParams(it, value)) }
    if (def.codec is FeatureCodec.StatusSwitchBit) return listOf(JsonObject(mapOf("status" to v)))
    val obj = key?.let { JsonObject(mapOf(it to v)) }
    if (obj != null && (def.codec == FeatureCodec.BareObjectFirst || def.choices != null)) {
        return listOf(obj, JsonArray(listOf(obj)), JsonArray(listOf(v)))
    }
    return listOfNotNull(JsonArray(listOf(obj ?: v)), obj, JsonArray(listOf(v)).takeIf { obj != null })
}

/** The get command for [def], with the params its codec needs. */
suspend fun VacuumSession.readFeatureRaw(def: BetaFeatureDef): V1Response = when {
    def.settingName != null -> getRobotSetting(def.settingName)
    def.codec is FeatureCodec.StatusSwitchBit -> sendCommandRaw("get_prop", GetStatusParams)
    def.codec is FeatureCodec.AiEnvironment -> readAiEnvironment(def.codec)
    else -> sendCommand(def.getMethod)
}

// The official page reads ground material, then furniture, then room type (p223 L979197).
private suspend fun VacuumSession.readAiEnvironment(c: FeatureCodec.AiEnvironment): V1Response {
    val ground = sendCommand(GET_GROUND_MATERIAL).also { if (it.error != null) return it }
    val furniture = sendCommand(GET_FURNITURE).also { if (it.error != null) return it }
    val room = if (c.withRoomType) runCatching { getRobotSetting(IDENTIFY_ROOM_SETTING) }.getOrNull()?.takeIf { it.error == null } else null
    return V1Response(furniture.id, aiEnvironmentAnswer(ground.result, furniture.result, room?.result), null)
}

/** The last reply of a write, and the [featureSetCandidates] index the robot took, if any. */
data class FeatureWrite(val method: String, val reply: V1Response?, val acceptedShape: Int?)

/** Writes [value] for [def], trying shapes from [lastAccepted] first; reads fresh state first where the write needs it. */
suspend fun VacuumSession.writeFeature(def: BetaFeatureDef, key: String?, value: Int, lastAccepted: Int?): FeatureWrite {
    when (val c = def.codec) {
        is FeatureCodec.CameraStatusBit -> {
            val word = cameraWord(getCameraStatus().requireOk("get_camera_status").result)
                ?: error("get_camera_status gave no word")
            if (value == 1 && cameraBitBlocked(c, word)) error(c.blockedReason)
            return FeatureWrite(def.setMethod, setCameraStatus(cameraWordFor(c, word, value == 1)), null)
        }
        is FeatureCodec.CameraStatusField -> {
            val word = cameraWord(getCameraStatus().requireOk("get_camera_status").result)
                ?: error("get_camera_status gave no word")
            return FeatureWrite(def.setMethod, setCameraStatus(cameraWordWithField(c, word, value)), null)
        }
        is FeatureCodec.AiEnvironment -> {
            // Same order as the official onSceneSwitch (p223 L978652), stopping at the first refusal.
            val body = JsonObject(mapOf("status" to JsonPrimitive(value)))
            sendCommandRaw(SET_GROUND_MATERIAL, body).requireOk(SET_GROUND_MATERIAL)
            val furniture = sendCommandRaw(SET_FURNITURE, body)
            if (!c.withRoomType || furniture.error != null) return FeatureWrite(SET_FURNITURE, furniture, null)
            return FeatureWrite("app_set_robot_setting", setRobotSetting(IDENTIFY_ROOM_SETTING, value), null)
        }
        FeatureCodec.DryerStartMethod -> {
            val reported = getDryerSetting().requireOk(def.getMethod).result.single() as? JsonObject
            return FeatureWrite(def.setMethod, setDryerSetting(dryerStartMethodBody(reported, value)), null)
        }
        FeatureCodec.CarpetModeEnable -> {
            val reported = sendCommand(def.getMethod).requireOk(def.getMethod).result.single() as? JsonObject
                ?: error("${def.getMethod} gave no object")
            return FeatureWrite(def.setMethod, sendCommandRaw(def.setMethod, carpetModeSetParams(reported, value)), null)
        }
        else -> Unit
    }
    val candidates = featureSetCandidates(def, key, value)
    var last: V1Response? = null
    var accepted: Int? = null
    for (i in betaSetTryOrder(candidates.size, lastAccepted)) {
        last = sendCommandRaw(def.setMethod, candidates[i])
        if (last.error == null) accepted = i
        if (last.error?.get("code")?.jsonPrimitive?.intOrNull !in BetaShapeRefusalCodes) break
    }
    return FeatureWrite(def.setMethod, last, accepted)
}
