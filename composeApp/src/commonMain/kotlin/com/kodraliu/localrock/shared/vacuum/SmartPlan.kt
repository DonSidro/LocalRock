package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Bit index of SmartPlan in `newFeatureSet` (python-roborock NewFeatureStrBit.SMART_CLEAN_MODE_SET). */
private const val SMART_CLEAN_MODE_SET_BIT = 55

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

fun isSmartPlanSupported(newFeatureSet: String?): Boolean = newFeatureBit(newFeatureSet, SMART_CLEAN_MODE_SET_BIT)

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
