package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A robot-reported capability, separate from whether LocalRock can edit its map format. */
enum class FurnitureSupport {
    UNKNOWN, UNSUPPORTED, SUPPORTED;

    val blockedReason: String? get() = when (this) {
        UNKNOWN -> "Furniture support could not be verified. Retry while the robot is connected."
        UNSUPPORTED -> "This robot does not report furniture support."
        SUPPORTED -> null
    }
}

/**
 * python-roborock DeviceFeatures.is_support_furniture: bit 4 of the upper 32 bits of
 * `new_feature_info` in app_get_init_status (bit 36 overall). This is the decimal integer,
 * not the separate hexadecimal `new_feature_info_str` / server `newFeatureSet` field.
 * Missing or malformed replies cannot authorize a map write. No model-name fallback.
 */
internal fun furnitureSupportFromInitStatus(result: JsonElement?): FurnitureSupport {
    val status = (if (result is JsonArray) result.singleOrNull() else result) as? JsonObject
        ?: return FurnitureSupport.UNKNOWN
    val text = (status["new_feature_info"] as? JsonPrimitive)?.contentOrNull
        ?: return FurnitureSupport.UNKNOWN
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return FurnitureSupport.UNKNOWN
    val flags = text.toULongOrNull() ?: return FurnitureSupport.UNKNOWN
    return if (flags and (1uL shl 36) != 0uL) FurnitureSupport.SUPPORTED else FurnitureSupport.UNSUPPORTED
}
