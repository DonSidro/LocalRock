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
/** The settings page a feature is shown on, following the official app's grouping. */
enum class FeatureSection { CARPET, FLOOR, OBSTACLE, DOCK, ROBOT, BETA }

/** One value of a multi-choice setting and the label shown for it. */
data class FeatureChoice(val value: Int, val label: String, val description: String? = null)

data class BetaFeatureDef(
    val id: String,
    val title: String,
    val description: String,
    val bit: Int?,
    val getMethod: String,
    val setMethod: String,
    val section: FeatureSection = FeatureSection.BETA,
    /** Set for options reached through app_get/set_robot_setting by name, not their own get/set pair. */
    val settingName: String? = null,
    /** Set for a setting with more than two values: shown as a picker, not a switch. */
    val choices: List<FeatureChoice>? = null,
    /** Id of an older option this one replaces on robots that advertise this one. */
    val replaces: String? = null,
    /**
     * Set when the value is one field of a larger reply, such as `switch_status` in get_status or `enable` in
     * get_carpet_mode, rather than the reply's only number.
     */
    val readKey: String? = null,
    /** Set when the option is some bits of the value read: it is on when any of them is set. */
    val readMask: Int? = null,
    /** The shape the set command takes. */
    val write: FeatureWrite = FeatureWrite.VALUE,
)

/** How an option's set command takes the new value. */
enum class FeatureWrite {
    /** The new value alone, in the shapes [betaSetParamCandidates] tries. */
    VALUE,

    /** `{"status": n}`, whatever the get reply looks like. */
    STATUS,

    /** The whole object read, with [BetaFeatureDef.readKey] changed, as a one-item list. */
    ECHO,
}

/**
 * The `switch_status` bit in get_status for Vacuum Carpet First. ioBroker.roborock reads the same field on a
 * Saros Z70 (0x1 offline map, 0x8 carpet first, 0x20 cross carpet cleaning); a Qrevo 2 Pro with the official
 * app's Vacuum Carpet First on and the other two off reports 8.
 */
const val SWITCH_STATUS_CARPET_FIRST = 0x8

val BetaFeatureDefs = listOf(
    BetaFeatureDef(
        id = "right_brush_stretch",
        title = "FlexiArm side brush",
        description = "Extends the side brush to reach into corners and along edges",
        bit = 54,
        getMethod = "get_right_brush_stretch_status",
        setMethod = "set_right_brush_stretch_status",
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "corner_mop_stretch",
        title = "FlexiArm mop",
        description = "Extends the mop to clean corners",
        bit = 40,
        getMethod = "get_stretch_tag_status",
        setMethod = "set_stretch_tag_status",
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "gap_deep_clean",
        title = "Gap deep clean",
        description = "Cleans narrow gaps along walls and furniture",
        bit = 63,
        getMethod = "get_gap_deep_clean_status",
        setMethod = "set_gap_deep_clean_status",
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "pet_supplies_deep_clean",
        title = "Pet supplies deep clean",
        description = "Extra cleaning around pet bowls and beds",
        bit = 43,
        getMethod = "get_pet_supplies_deep_clean_status",
        setMethod = "set_pet_supplies_deep_clean_status",
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "dirty_object_detect",
        title = "Dirty object detection",
        description = "Spots dirty objects such as pet waste while cleaning",
        bit = 56,
        getMethod = "get_dirty_object_detect_status",
        setMethod = "set_dirty_object_detect_status",
        section = FeatureSection.OBSTACLE,
    ),
    BetaFeatureDef(
        id = "avoid_collision",
        title = "Collision avoidance",
        description = "Steers around obstacles instead of bumping them",
        bit = 36,
        getMethod = "get_collision_avoid_status",
        setMethod = "set_collision_avoid_status",
        section = FeatureSection.OBSTACLE,
    ),
    // With bit 118 (python-roborock TWO_GEARS_NO_COLLISION) the official app shows the same value as a
    // two-way picker: a Qrevo 2 Pro reporting 1 showed "High Coverage". 2 as Less Collision follows an
    // S8 MaxV that went from 1 to 2 when its owner chose the careful setting. 0 is not offered there.
    BetaFeatureDef(
        id = "collision_mode",
        title = "Less collision mode",
        description = "How closely the robot works around obstacles",
        bit = 118,
        getMethod = "get_collision_avoid_status",
        setMethod = "set_collision_avoid_status",
        section = FeatureSection.OBSTACLE,
        choices = listOf(
            FeatureChoice(1, "High coverage", "Reaches more of the floor and cleans closer, and may touch obstacles lightly"),
            FeatureChoice(2, "Less collision", "Moves more carefully to avoid bumping or getting stuck, and may miss some spots"),
        ),
        replaces = "avoid_collision",
        write = FeatureWrite.STATUS,
    ),
    BetaFeatureDef(
        id = "water_leak_check",
        title = "Water leak detection",
        description = "Stops and alerts if the robot detects a water leak",
        bit = 60,
        getMethod = "get_handle_leak_water_status",
        setMethod = "set_handle_leak_water_status",
        section = FeatureSection.ROBOT,
    ),
    BetaFeatureDef(
        id = "voice_control_led",
        title = "Voice control light",
        description = "Light on the robot that shows when it is listening",
        bit = 58,
        getMethod = "get_ap_mic_led_status",
        setMethod = "set_ap_mic_led_status",
        section = FeatureSection.ROBOT,
    ),
    BetaFeatureDef(
        id = "carpet_first",
        title = "Vacuum carpet first",
        description = "Vacuums carpets before mopping the rest, so the mop never wets them",
        bit = 23,
        getMethod = "get_status",
        setMethod = "app_set_priority_carpet_cleaning_status",
        section = FeatureSection.CARPET,
        readKey = "switch_status",
        readMask = SWITCH_STATUS_CARPET_FIRST,
        write = FeatureWrite.STATUS,
    ),
    BetaFeatureDef(
        id = "carpet_boost",
        title = "Carpet boost",
        description = "Turns suction up to maximum on carpet",
        bit = null,
        getMethod = "get_carpet_mode",
        setMethod = "set_carpet_mode",
        section = FeatureSection.CARPET,
        readKey = "enable",
        write = FeatureWrite.ECHO,
    ),
    BetaFeatureDef(
        id = "carpet_deep_clean",
        title = "Carpet deep clean",
        description = "Deeper cleaning passes over carpets",
        bit = 3,
        getMethod = "app_get_carpet_deep_clean_status",
        setMethod = "app_set_carpet_deep_clean_status",
        section = FeatureSection.CARPET,
    ),
    BetaFeatureDef(
        id = "avoid_carpet_wires",
        title = "Avoid wires along carpet borders",
        description = "Detects cables near carpet edges and keeps clear of them",
        bit = 105,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.CARPET,
        settingName = "nearbycarpet_detecting_wire_status",
    ),
    BetaFeatureDef(
        id = "carpet_side_brush_lift",
        title = "Lower side brush on carpet",
        description = "Keeps the side brush down while cleaning carpets",
        bit = 104,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.CARPET,
        settingName = "disable_sidebrush_lift_status",
    ),
    BetaFeatureDef(
        id = "clean_fluid_delivery",
        title = "Auto cleaning fluid",
        description = "Adds cleaning fluid to the mop water from the dock",
        bit = 112,
        getMethod = "get_auto_delivery_cleaning_fluid",
        setMethod = "set_auto_delivery_cleaning_fluid",
        section = FeatureSection.DOCK,
    ),
    BetaFeatureDef(
        id = "dust_collection_switch",
        title = "Auto empty",
        description = "Empties the dust bin into the dock after cleaning",
        bit = null,
        getMethod = "get_dust_collection_switch_status",
        setMethod = "set_dust_collection_switch_status",
        section = FeatureSection.DOCK,
    ),
    // With bit 53 (python-roborock AUTO_COLLECTION_2) the same value is a frequency: a Qrevo 2 Pro reporting
    // 1 showed "Balanced" in the official app. 2 as High follows python-roborock's open PR #1036 (Saros 10R).
    BetaFeatureDef(
        id = "dust_collection_frequency",
        title = "Auto-empty frequency",
        description = "When the dock empties the dustbin",
        bit = 53,
        getMethod = "get_dust_collection_switch_status",
        setMethod = "set_dust_collection_switch_status",
        section = FeatureSection.DOCK,
        choices = listOf(
            FeatureChoice(0, "Off", "Never; empty the dustbin by hand"),
            FeatureChoice(1, "Balanced", "After each clean"),
            FeatureChoice(2, "High", "During cleaning as well, for homes with pets or a lot of carpet"),
        ),
        replaces = "dust_collection_switch",
        write = FeatureWrite.STATUS,
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
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "follow_ground_material",
        title = "Clean by floor type",
        description = "Adjusts cleaning to the floor type it detects",
        bit = null,
        getMethod = "get_clean_follow_ground_material_status",
        setMethod = "set_clean_follow_ground_material_status",
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "uncleaned_area_reminder",
        title = "Uncleaned area reminder",
        description = "Tells you about any uncleaned area after each clean, with a one-tap option to clean it",
        bit = 154,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.ROBOT,
        settingName = "enable_omission_area_clean_status",
    ),
    BetaFeatureDef(
        id = "follow_low_obstacles",
        title = "Follow low obstacles",
        description = "Lets the robot follow low obstacles",
        bit = 117,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.OBSTACLE,
        settingName = "enable_follow_low_obs_status",
    ),
    BetaFeatureDef(
        id = "full_duplex",
        title = "Full duplex voice",
        description = "Talk and listen at the same time during voice chat",
        bit = 115,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.ROBOT,
        settingName = "full_duplex_switch",
    ),
    BetaFeatureDef(
        id = "climbing_arms_high_sill",
        title = "Climbing arms on high thresholds",
        description = "Uses the climbing arms to cross high door thresholds and carpet",
        bit = 147,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.FLOOR,
        settingName = "enable_sway_arm_for_high_door_sill_carpet",
    ),
    BetaFeatureDef(
        id = "cleaning_pattern",
        title = "Cleaning pattern",
        description = "How the robot lines up its passes",
        bit = 70,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.FLOOR,
        settingName = "clean_direction_status",
        choices = listOf(
            FeatureChoice(0, "Smart", "Runs along the long side of the room"),
            FeatureChoice(1, "Along floor direction", "Follows the floor boards or tiles"),
            FeatureChoice(2, "Mixed pattern", "Crosses the last clean's direction, which is slower"),
        ),
        replaces = "follow_ground_material",
    ),
    BetaFeatureDef(
        id = "optimize_battery",
        title = "Smart charging",
        description = "Charges to 80% first, then slowly to 100%, to protect the battery",
        bit = 74,
        getMethod = "get_optimize_battery_status",
        setMethod = "set_optimize_battery_status",
        section = FeatureSection.ROBOT,
    ),
)

/** The features this robot's `newFeatureSet` advertises. */
fun supportedBetaFeatures(newFeatureSet: String?): List<BetaFeatureDef> =
    BetaFeatureDefs.filter { it.bit == null || newFeatureBit(newFeatureSet, it.bit) }

/**
 * The order to read [defs] in: options gated on a feature bit first, then the ones every robot is
 * asked. A read stops at the first option with no reply, so a robot that ignores one of the
 * ungated commands then hides only the ungated options, not the ones its flags advertise.
 */
fun betaReadOrder(defs: List<BetaFeatureDef>): List<BetaFeatureDef> = defs.sortedBy { it.bit == null }

/** Whether the Beta page has anything to show: a flag-gated switch of its own, or flags with no switch here. */
fun showsBetaPage(defs: List<BetaFeatureDef>, unlisted: List<AdvertisedFeature>): Boolean =
    defs.any { it.section == FeatureSection.BETA && it.bit != null } || unlisted.isNotEmpty()

/** Drops an older option once the option that replaces it has answered, so a failed read keeps the old one. */
fun List<BetaFeatureState>.withoutReplaced(): List<BetaFeatureState> {
    val replaced = mapNotNull { it.def.replaces }.toSet()
    return filter { it.def.id !in replaced }
}

/** The reads of [features] that belong on [section], in the order the definitions list them. */
fun List<BetaFeatureState>.inSection(section: FeatureSection): List<BetaFeatureState> =
    filter { it.def.section == section }

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

/** Bits with their own place in the app (Matter among them) or that name no setting; the per-bit reasons are in the tests. */
private val HandledElsewhereBits = setOf(35, 38, 41, 42, 47, 49, 51, 52, 53, 55, 57, 62, 67, 106)

/** A `newFeatureSet` bit the robot advertises that has no switch here; [name] is null when python-roborock has none. */
data class AdvertisedFeature(val bit: Int, val name: String?)

/** What the robot advertises in `newFeatureSet` (bit 32 and up) that has no switch here, in bit order. */
fun unlistedAdvertisedFeatures(newFeatureSet: String?): List<AdvertisedFeature> {
    val s = newFeatureSet ?: return emptyList()
    val covered = BetaFeatureDefs.mapNotNull { it.bit }.toSet() + HandledElsewhereBits + VOICE_ASSISTANT_BIT
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
    /** The object [value] was read from, for an option written back whole ([FeatureWrite.ECHO]). */
    val reply: JsonObject? = null,
) {
    val enabled: Boolean get() = def.readMask?.let { mask -> value != null && (value and mask) != 0 } ?: (value == 1)
    val toggleable: Boolean get() = when {
        def.choices != null || def.readMask != null -> value != null
        else -> value == 0 || value == 1
    }

    /** The label for the current value of a multi-choice setting; a value the app has no name for shows as "Other (n)". */
    val choiceLabel: String? get() = value?.let { v -> def.choices?.let { choiceLabelFor(it, v) } }
}

fun choiceLabelFor(choices: List<FeatureChoice>, value: Int): String =
    choices.firstOrNull { it.value == value }?.label ?: "Other ($value)"

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

/** One option's value as read: the reply key it sits under, the number, and the object it came from. */
data class FeatureReading(val key: String?, val value: Int, val reply: JsonObject? = null)

/** Reads [def]'s value from its get reply: the [BetaFeatureDef.readKey] field when it has one, else the reply's only number. */
fun parseFeatureReply(def: BetaFeatureDef, result: JsonElement?): FeatureReading? {
    val readKey = def.readKey ?: return parseBetaReply(result)?.let { (key, value) -> FeatureReading(key, value) }
    val obj = (if (result is JsonArray && result.size == 1) result[0] else result) as? JsonObject ?: return null
    val value = (obj[readKey] as? JsonPrimitive)?.intOrNull ?: return null
    return FeatureReading(readKey, value, obj)
}

/**
 * Param shapes to try for a set command, in order. python-roborock does not document these, so the
 * first is the wrapped form of the get reply and the rest are the other usual forms.
 */
fun betaSetParamCandidates(key: String?, on: Boolean): List<JsonElement> = betaSetParamCandidates(key, if (on) 1 else 0)

fun betaSetParamCandidates(key: String?, value: Int): List<JsonElement> {
    val v = JsonPrimitive(value)
    val obj = key?.let { JsonObject(mapOf(it to v)) }
    return listOfNotNull(
        JsonArray(listOf(obj ?: v)),
        obj,
        JsonArray(listOf(v)).takeIf { obj != null },
    )
}

/** Param shapes to try for writing [value] to [state]'s option, in order; empty when the last read lacks what the write needs. */
fun featureSetParamCandidates(state: BetaFeatureState, value: Int): List<JsonElement> = when (state.def.write) {
    FeatureWrite.VALUE -> betaSetParamCandidates(state.key, value)
    FeatureWrite.STATUS -> JsonObject(mapOf("status" to JsonPrimitive(value))).let { listOf(it, JsonArray(listOf(it))) }
    FeatureWrite.ECHO -> {
        val key = state.def.readKey
        val reply = state.reply
        if (key == null || reply == null) {
            emptyList()
        } else {
            val obj = JsonObject(reply + (key to JsonPrimitive(value)))
            listOf(JsonArray(listOf(obj)), obj)
        }
    }
}

/**
 * Codes a robot answers to a param shape it does not take. Most options say -10007 "invalid params";
 * a Qrevo Curv 2 Flow (a245) says -10005 "Invalid parameters" to the wrapped set_identify_ground_material_status,
 * as a Qrevo Pro does to the wrapped set_child_lock_status.
 */
val BetaShapeRefusalCodes = setOf(-10007, -10005)

/** Indices into the [betaSetParamCandidates] list in the order to try them, the shape the robot last took first. */
fun betaSetTryOrder(count: Int, accepted: Int?): List<Int> {
    val all = (0 until count).toList()
    return if (accepted == null || accepted !in all) all else listOf(accepted) + all.filter { it != accepted }
}
