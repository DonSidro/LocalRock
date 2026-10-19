package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A robot setting that newer firmware advertises through a `newFeatureSet` bit and exposes as a
 * `get_*` / `set_*` command pair. Bit indexes and command names follow python-roborock
 * (NewFeatureStrBit, RoborockCommand); a null [bit] means no `newFeatureSet` bit is known for it, so
 * it shows only if the robot answers its get command with 0 or 1; the set payload is not documented there, so it mirrors the
 * shape of the get reply (see [parseBetaReply] and [encodeBetaSetParams]).
 */
data class BetaFeatureDef(
    val id: String,
    val title: String,
    val description: String,
    val bit: Int?,
    val getMethod: String,
    val setMethod: String,
)

val BetaFeatureDefs = listOf(
    BetaFeatureDef(
        id = "right_brush_stretch",
        title = "FlexiArm side brush",
        description = "Extends the side brush to reach into corners and along edges",
        bit = 54,
        getMethod = "get_right_brush_stretch_status",
        setMethod = "set_right_brush_stretch_status",
    ),
    BetaFeatureDef(
        id = "corner_mop_stretch",
        title = "FlexiArm mop",
        description = "Extends the mop to clean corners",
        bit = 40,
        getMethod = "get_stretch_tag_status",
        setMethod = "set_stretch_tag_status",
    ),
    BetaFeatureDef(
        id = "gap_deep_clean",
        title = "Gap deep clean",
        description = "Cleans narrow gaps along walls and furniture",
        bit = 63,
        getMethod = "get_gap_deep_clean_status",
        setMethod = "set_gap_deep_clean_status",
    ),
    BetaFeatureDef(
        id = "pet_supplies_deep_clean",
        title = "Pet supplies deep clean",
        description = "Extra cleaning around pet bowls and beds",
        bit = 43,
        getMethod = "get_pet_supplies_deep_clean_status",
        setMethod = "set_pet_supplies_deep_clean_status",
    ),
    BetaFeatureDef(
        id = "dirty_object_detect",
        title = "Dirty object detection",
        description = "Spots dirty objects such as pet waste while cleaning",
        bit = 56,
        getMethod = "get_dirty_object_detect_status",
        setMethod = "set_dirty_object_detect_status",
    ),
    BetaFeatureDef(
        id = "avoid_collision",
        title = "Collision avoidance",
        description = "Steers around obstacles instead of bumping them",
        bit = 36,
        getMethod = "get_collision_avoid_status",
        setMethod = "set_collision_avoid_status",
    ),
    BetaFeatureDef(
        id = "water_leak_check",
        title = "Water leak detection",
        description = "Stops and alerts if the robot detects a water leak",
        bit = 60,
        getMethod = "get_handle_leak_water_status",
        setMethod = "set_handle_leak_water_status",
    ),
    BetaFeatureDef(
        id = "voice_control_led",
        title = "Voice control light",
        description = "Light on the robot that shows when it is listening",
        bit = 58,
        getMethod = "get_ap_mic_led_status",
        setMethod = "set_ap_mic_led_status",
    ),
    BetaFeatureDef(
        id = "carpet_deep_clean",
        title = "Carpet deep clean",
        description = "Deeper cleaning passes over carpets",
        bit = 3,
        getMethod = "app_get_carpet_deep_clean_status",
        setMethod = "app_set_carpet_deep_clean_status",
    ),
    BetaFeatureDef(
        id = "clean_fluid_delivery",
        title = "Auto cleaning fluid",
        description = "Adds cleaning fluid to the mop water from the dock",
        bit = 112,
        getMethod = "get_auto_delivery_cleaning_fluid",
        setMethod = "set_auto_delivery_cleaning_fluid",
    ),
    BetaFeatureDef(
        id = "dust_collection_switch",
        title = "Auto empty",
        description = "Empties the dust bin into the dock after cleaning",
        bit = null,
        getMethod = "get_dust_collection_switch_status",
        setMethod = "set_dust_collection_switch_status",
    ),
    BetaFeatureDef(
        id = "identify_furniture",
        title = "Furniture recognition",
        description = "Recognizes furniture on the map",
        bit = null,
        getMethod = "get_identify_furniture_status",
        setMethod = "set_identify_furniture_status",
    ),
    BetaFeatureDef(
        id = "identify_ground_material",
        title = "Floor type recognition",
        description = "Recognizes carpet and hard floor",
        bit = null,
        getMethod = "get_identify_ground_material_status",
        setMethod = "set_identify_ground_material_status",
    ),
    BetaFeatureDef(
        id = "follow_ground_material",
        title = "Clean by floor type",
        description = "Adjusts cleaning to the floor type it detects",
        bit = null,
        getMethod = "get_clean_follow_ground_material_status",
        setMethod = "set_clean_follow_ground_material_status",
    ),
)

/** The features this robot's `newFeatureSet` advertises. */
fun supportedBetaFeatures(newFeatureSet: String?): List<BetaFeatureDef> =
    BetaFeatureDefs.filter { it.bit == null || newFeatureBit(newFeatureSet, it.bit) }

/**
 * Names for `newFeatureSet` bits from 32 up (python-roborock NewFeatureStrBit), for listing what a
 * robot advertises that LocalRock has no controls for. Bits the table above covers and bits the app
 * handles elsewhere are left out of that list.
 */
private val AdvertisedFeatureNames = mapOf(
    32 to "Two-key real-time video",
    33 to "Two-key video while charging",
    34 to "Dirty replenish clean",
    35 to "Auto delivery status",
    37 to "Voice control",
    38 to "New endpoint",
    39 to "Pumping water",
    41 to "Hot wash towel",
    42 to "Floor direction clean any time",
    45 to "Mop shake water max",
    47 to "Exact custom mode",
    48 to "Video patrol",
    49 to "Carpet custom clean",
    50 to "Pet snapshot",
    51 to "Custom clean mode count",
    52 to "New AI recognition",
    53 to "Auto collection 2",
    57 to "No carpet press setting",
    62 to "Min battery 15% to clean",
    64 to "Object detect check",
    66 to "Identify room",
    67 to "Matter",
    69 to "Workday and holiday",
    70 to "Clean direct status",
    71 to "Map eraser",
    72 to "Optimize battery",
    73 to "Video while charging and standby",
    75 to "Long-haired carpet",
    76 to "Clean history timeline",
    77 to "Max zones opened",
    78 to "Exhibition function",
    79 to "LDS lifting",
    80 to "Auto tear down mop",
    81 to "Small side mop",
    82 to "Side brush up and down",
    83 to "Dry interval timer",
    84 to "UVC sterilize",
    85 to "Midway back to dock",
    86 to "Main brush up and down",
    87 to "Egg dance mode",
    89 to "Mechanical arm mode",
    91 to "Clean timeline",
    93 to "Clean then mop mode",
    94 to "Type identify",
    96 to "Get particular status",
    97 to "3D mapping inner test",
    98 to "Sync server name",
    99 to "Show arm overload",
    100 to "Collect dust count",
    101 to "App stop grasp",
    102 to "Clean then mop with repeat",
    104 to "Side brush lift on carpet",
    105 to "Detect wire carpet",
    107 to "Soak and wash",
    108 to "Clean efficiency",
    109 to "Back wash new smart",
    110 to "Dual-band Wi-Fi",
    111 to "Program mode",
    113 to "Long-haired carpet EX",
    114 to "Overseas clean then mop",
    115 to "Full duplex switch",
    116 to "Low area access",
    117 to "Follow low obstacles",
    118 to "Two gears no collision",
    119 to "Carpet shape type",
    120 to "SR map",
    128 to "Roller mop",
)

/** Bits that have their own place in the app (SmartPlan, water slide), so they are not listed as missing. */
private val HandledElsewhereBits = setOf(55, 106)

/** A `newFeatureSet` bit the robot advertises that has no switch here; [name] is null when python-roborock has none. */
data class AdvertisedFeature(val bit: Int, val name: String?)

/** What the robot advertises in `newFeatureSet` (bit 32 and up) that has no switch here, in bit order. */
fun unlistedAdvertisedFeatures(newFeatureSet: String?): List<AdvertisedFeature> {
    val s = newFeatureSet ?: return emptyList()
    val covered = BetaFeatureDefs.mapNotNull { it.bit }.toSet() + HandledElsewhereBits
    return (32 until s.length * 4)
        .filter { it !in covered && newFeatureBit(s, it) }
        .map { AdvertisedFeature(it, AdvertisedFeatureNames[it]) }
}

/** Last read of one feature. [key] is the field name in the reply, null when the reply was a bare number. */
data class BetaFeatureState(
    val def: BetaFeatureDef,
    val value: Int? = null,
    val key: String? = null,
    val raw: String? = null,
    val answered: Boolean = true,
) {
    val enabled: Boolean get() = value == 1
    val toggleable: Boolean get() = value == 0 || value == 1
}

/** Reads a reply that holds one number: `1`, `[1]`, `{"status":1}` or `[{"status":1}]`. */
fun parseBetaReply(result: JsonElement?): Pair<String?, Int>? {
    val element = if (result is JsonArray && result.size == 1) result[0] else result
    return when (element) {
        is JsonPrimitive -> element.intOrNull?.let { null to it }
        is JsonObject -> {
            val entry = element.entries.singleOrNull() ?: return null
            (entry.value as? JsonPrimitive)?.intOrNull?.let { entry.key to it }
        }
        else -> null
    }
}

/**
 * Param shapes to try for a set command, in order. The robot answers "invalid params" (-10007) to a
 * shape it does not take, and python-roborock does not document these, so the first is the wrapped
 * form of the get reply (like set_child_lock) and the rest are the other usual forms.
 */
fun betaSetParamCandidates(key: String?, on: Boolean): List<JsonElement> {
    val v = JsonPrimitive(if (on) 1 else 0)
    val obj = key?.let { JsonObject(mapOf(it to v)) }
    return listOfNotNull(
        JsonArray(listOf(obj ?: v)),
        obj,
        JsonArray(listOf(v)).takeIf { obj != null },
    )
}
