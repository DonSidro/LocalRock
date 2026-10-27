package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Bit index of SmartPlan in `newFeatureSet` (python-roborock NewFeatureStrBit.SMART_CLEAN_MODE_SET). */
private const val SMART_CLEAN_MODE_SET_BIT = 55

/** Bit index of water slide mode (python-roborock NewFeatureStrBit.WATER_SLIDE_MODE). */
private const val WATER_SLIDE_MODE_BIT = 106

/**
 * Reads bit [bit] of the hex `newFeatureSet` string, counting from its last character.
 * Port of python-roborock's DeviceFeatures.from_feature_flags; anything malformed reads as false.
 */
fun newFeatureBit(newFeatureSet: String?, bit: Int): Boolean {
    val s = newFeatureSet ?: return false
    val fromEnd = 1 + bit / 4
    if (fromEnd > s.length) return false
    val nibble = s[s.length - fromEnd].digitToIntOrNull(16) ?: return false
    return (nibble shr (bit % 4)) and 1 == 1
}

/**
 * The robot's own feature flags from its `app_get_init_status` answer, the hex string the server keeps
 * as `newFeatureSet`. A robot onboarded locally never gets that field filled in on the server.
 */
fun featureSetFromInitStatus(result: JsonElement?): String? {
    val status = (if (result is JsonArray) result.firstOrNull() else result) as? JsonObject ?: return null
    val text = (status["new_feature_info_str"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    return text.takeIf { it.isNotEmpty() && it.all { c -> c.digitToIntOrNull(16) != null } }
}

fun isSmartPlanSupported(newFeatureSet: String?): Boolean = newFeatureBit(newFeatureSet, SMART_CLEAN_MODE_SET_BIT)

/** Water slide robots use their own water codes (221-250) and may not accept 201-203. */
fun isWaterSlideSupported(newFeatureSet: String?): Boolean = newFeatureBit(newFeatureSet, WATER_SLIDE_MODE_BIT)

/** SmartPlan is on if any of the three values is its smart code (python-roborock is_smart_mode_set). */
fun isSmartModeSet(fanPower: Int?, waterBoxMode: Int?, mopMode: Int?): Boolean =
    fanPower == VacuumFanPower.SMART || waterBoxMode == WaterBoxMode.SMART || mopMode == MopRoute.SMART

/** Params for `set_clean_motor_mode`, which sets all three at once like python-roborock's set_cleaning_mode. */
fun encodeCleanMotorModeParams(fanPower: Int, waterBoxMode: Int, mopMode: Int): JsonArray =
    JsonArray(listOf(JsonObject(mapOf(
        "fan_power" to JsonPrimitive(fanPower),
        "water_box_mode" to JsonPrimitive(waterBoxMode),
        "mop_mode" to JsonPrimitive(mopMode),
    ))))

/** The suction, water and route codes one `set_clean_motor_mode` call carries. */
data class MotorModes(val fanPower: Int, val waterBoxMode: Int, val mopMode: Int)

/**
 * The robot's current three codes to resend with a single change, when a refusal of
 * set_custom_mode, set_water_box_custom_mode or set_mop_mode is worth retrying as one
 * set_clean_motor_mode call; null when it is not. A Saros 20 Neo (a296, 02.29.56) answers -10005 to
 * set_custom_mode 102-105 and to some set_mop_mode values that the combined call accepts (#34).
 * Only -10005 is retried, and only with all three known. The codes stay raw, as the robot reported
 * them (water 235, not normalized to 202), since those are what it accepted. SmartPlan and per-room
 * settings own all three, so they are left alone.
 */
internal fun motorModesForFallback(errorCode: Int?, fanPower: Int?, waterBoxMode: Int?, mopMode: Int?): MotorModes? {
    if (errorCode != PARAM_FORMAT_REFUSED_CODE) return null
    if (fanPower == null || waterBoxMode == null || mopMode == null) return null
    if (isSmartModeSet(fanPower, waterBoxMode, mopMode) || isCustomizeModeSet(fanPower, waterBoxMode, mopMode)) return null
    return MotorModes(fanPower, waterBoxMode, mopMode)
}
