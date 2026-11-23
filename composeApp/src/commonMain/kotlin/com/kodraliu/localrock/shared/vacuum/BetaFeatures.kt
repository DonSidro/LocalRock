package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.vacuum.camera.CAMERA_FEATURE_BITS
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
    /** Set when the RPCs differ from the plain get/set pair (see FeatureCodecs.kt). */
    val codec: FeatureCodec? = null,
    /** The official app hides this option on robots that advertise this bit. */
    val hiddenByBit: Int? = null,
    /** Only on the official app's hidden debug page; listed here so the bit counts as known, never shown. */
    val debugOnly: Boolean = false,
    /** Shown only on robots that advertise at least one of these bits. */
    val anyOfBits: List<Int>? = null,
)

// The camera_status bits the obstacle rows use; the official updateCameraStatus (p223 L309312) packs them.
private const val CAMERA_ON_BIT = 0
private const val PET_MODE_BIT = 1
private const val EXPLORATION_BIT = 6
private const val OBSTACLE_PHOTO_BIT = 10
private const val PET_SNAPSHOT_BIT = 14

const val CARPET_CROSS_ID = "carpet_cross"

// The official app shows bit 64's two switches only on a hidden debug page, and turning them off weakens obstacle avoidance.
const val SHOW_DEBUG_FEATURES = false

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
        section = FeatureSection.FLOOR,
    ),
    BetaFeatureDef(
        id = "avoid_collision",
        title = "Collision avoidance",
        description = "Steers around obstacles instead of bumping them",
        bit = 36,
        getMethod = "get_collision_avoid_status",
        setMethod = "set_collision_avoid_status",
        section = FeatureSection.OBSTACLE,
        hiddenByBit = 118,
    ),
    // Two-gear robots (bit 118) take only 1 and 2 here; the official app never sends 0 to them.
    BetaFeatureDef(
        id = "less_collision_mode",
        title = "Less Collision Mode", // verified
        description = "Choose the sensitivity of obstacle avoidance based on the surroundings.", // verified
        bit = 118,
        getMethod = "get_collision_avoid_status",
        setMethod = "set_collision_avoid_status",
        section = FeatureSection.OBSTACLE,
        choices = listOf(
            FeatureChoice(1, "High Coverage", "Intelligently covers more areas for deeper cleaning. May cause slight bumps."), // verified
            FeatureChoice(2, "Less Collision", "Moves more safely to avoid collisions and getting stuck. High cleaning efficiency but may miss some spots."), // verified
        ),
    ),
    // Off also turns off obstacle photos, pet snaps and exploration, as the official updateAiRecognitionState does.
    BetaFeatureDef(
        id = "obstacle_avoidance",
        title = "Reactive Obstacle Avoidance", // verified
        description = "Intelligently recognizes and avoids obstacles that may block its way.", // verified
        bit = null,
        getMethod = "get_camera_status",
        setMethod = "set_camera_status",
        section = FeatureSection.OBSTACLE,
        codec = FeatureCodec.CameraStatusBit(CAMERA_ON_BIT, emptyList(), "", clearsWhenOff = listOf(EXPLORATION_BIT, OBSTACLE_PHOTO_BIT, PET_SNAPSHOT_BIT)),
    ),
    BetaFeatureDef(
        id = "obstacle_photos",
        title = "Obstacle Photos", // verified
        description = "Tap the icons on the map to view images of obstacles identified by the camera.", // verified
        bit = null,
        getMethod = "get_camera_status",
        setMethod = "set_camera_status",
        section = FeatureSection.OBSTACLE,
        codec = FeatureCodec.CameraStatusBit(OBSTACLE_PHOTO_BIT, listOf(CAMERA_ON_BIT), "Enable Reactive Obstacle Avoidance", listOf(PET_SNAPSHOT_BIT)), // verified
        // Needs a camera: the Pro answers get_camera_status with only the avoidance bit and has no video bits.
        anyOfBits = CAMERA_FEATURE_BITS,
    ),
    BetaFeatureDef(
        id = "pet_mode",
        title = "Pet", // verified
        description = "When the robot recognizes cats, dogs, and feces during operation, it takes more cautious avoidance actions.\n*Due to the influence of ground environment, pet posture, feces shape and size, it cannot be 100% recognized.", // verified
        bit = null,
        getMethod = "get_camera_status",
        setMethod = "set_camera_status",
        section = FeatureSection.OBSTACLE,
        // The official row is disabled while avoidance is off; the tip text is the photo row's toast.
        codec = FeatureCodec.CameraStatusBit(PET_MODE_BIT, listOf(CAMERA_ON_BIT), "Enable Reactive Obstacle Avoidance", listOf(PET_SNAPSHOT_BIT)), // inferred
        anyOfBits = CAMERA_FEATURE_BITS,
    ),
    BetaFeatureDef(
        id = "pet_snapshot",
        title = "Pet Snaps", // verified
        description = "The robot snaps your pets during cleaning.", // verified
        bit = 50,
        getMethod = "get_camera_status",
        setMethod = "set_camera_status",
        section = FeatureSection.OBSTACLE,
        codec = FeatureCodec.CameraStatusBit(PET_SNAPSHOT_BIT, listOf(PET_MODE_BIT, OBSTACLE_PHOTO_BIT), "Enable Obstacle Photos and Pet mode first"), // verified
    ),
    // Official gate is MF.AIRecognition_Scene, which LocalRock has no table for: shown when both reads answer.
    BetaFeatureDef(
        id = "ai_environment",
        title = "AI Environment Recognition", // verified
        description = "Actively perceives the home environment and identifies some furniture and floor types.", // verified
        bit = null,
        getMethod = GET_GROUND_MATERIAL,
        setMethod = SET_GROUND_MATERIAL,
        section = FeatureSection.OBSTACLE,
        codec = FeatureCodec.AiEnvironment(),
    ),
    // camera_status bits 3-4 (p223 L309225); the official picker writes 0 or 2 (L978862). Official gate MF.Camera_RGB.
    BetaFeatureDef(
        id = "fill_light",
        title = "Fill Light", // verified
        description = "Turns on the fill light in low-light conditions to enhance obstacle recognition and remote viewing.", // verified
        bit = null,
        getMethod = "get_camera_status",
        setMethod = "set_camera_status",
        section = FeatureSection.OBSTACLE,
        codec = FeatureCodec.CameraStatusField(shift = 3, width = 2),
        choices = listOf(
            FeatureChoice(0, "Automatic"), // verified
            FeatureChoice(2, "Off", "Recognition results of objects will be affected when the fill light is off."), // verified
        ),
        anyOfBits = CAMERA_FEATURE_BITS,
    ),
    BetaFeatureDef(
        id = "identify_room",
        title = "Smart Room Type Recognition", // verified text, inferred mapping to identify_room_status
        description = "Recognizes room types on the map", // inferred
        bit = 66,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        settingName = "identify_room_status",
    ),
    BetaFeatureDef(
        id = "common_obs_avoidance",
        title = "General object recognition", // inferred, the official title is Chinese only
        description = "Debug switch from the official app's hidden page", // inferred
        bit = 64,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.OBSTACLE,
        settingName = "common_obs_avoidance_status",
        debugOnly = true,
    ),
    BetaFeatureDef(
        id = "object_detect_avoidance",
        title = "Object-like recognition", // inferred, the official title is Chinese only
        description = "Debug switch from the official app's hidden page", // inferred
        bit = 64,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.OBSTACLE,
        settingName = "object_detect_avoidance_status",
        debugOnly = true,
    ),
    BetaFeatureDef(
        id = "auto_remop",
        title = "Automatic Re-Mopping", // verified
        description = "When a dirty area is detected during mopping, robot will return to the dock for mop washing and double mop after.", // verified
        bit = 34,
        getMethod = "get_prop",
        setMethod = "app_set_dirty_replenish_clean_status",
        section = FeatureSection.FLOOR,
        codec = FeatureCodec.StatusSwitchBit(0x10),
    ),
    BetaFeatureDef(
        id = "voice_assistant",
        title = "Voice Assistant", // verified
        description = "The robot will respond to your voice commands.", // verified
        bit = 37,
        getMethod = "get_voice_service_switch",
        setMethod = "set_voice_service_switch",
        section = FeatureSection.ROBOT,
        codec = FeatureCodec.BareObjectFirst,
    ),
    BetaFeatureDef(
        id = "drying_start_method",
        title = "Drying Start Method", // verified
        description = "Adjust the auto-drying frequency after cleaning.", // verified
        bit = 83,
        getMethod = "app_get_dryer_setting",
        setMethod = "app_set_dryer_setting",
        section = FeatureSection.DOCK,
        codec = FeatureCodec.DryerStartMethod,
        choices = listOf(
            FeatureChoice(0, "Manually Start Drying", "Auto-drying will not start after mop washing. You need to start it manually in the app."), // verified
            FeatureChoice(1, "Auto Drying", "Auto-drying will be performed after mop washing to avoid dampness and odors."), // verified (p223 L497816)
            FeatureChoice(2, "Scheduled Drying", "If the robot detects a mop washing task, it will automatically start the drying process at the preset time."), // verified
        ),
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
        title = "Vacuum Carpet First", // verified
        description = "Lift mop cloth mounts to vacuum carpets first, then clean other areas.\n*Not including mats.", // verified
        bit = 23,
        getMethod = "get_prop",
        setMethod = "app_set_priority_carpet_cleaning_status",
        section = FeatureSection.CARPET,
        codec = FeatureCodec.StatusSwitchBit(0x08),
    ),
    // Shown as the Cross carpet mode card, not as a switch row.
    BetaFeatureDef(
        id = CARPET_CROSS_ID,
        title = "Cross", // verified
        description = "The robot will cross carpeted areas only when necessary, with the mop cloths lifted.", // verified
        bit = 49,
        getMethod = "get_prop",
        setMethod = "app_set_cross_carpet_cleaning_status",
        section = FeatureSection.CARPET,
        codec = FeatureCodec.StatusSwitchBit(0x20),
    ),
    BetaFeatureDef(
        id = "carpet_boost",
        title = "Carpet Boost", // verified
        description = "The robot will automatically detect carpets and turn suction power to maximum for a deeper clean.", // verified
        bit = null,
        getMethod = "get_carpet_mode",
        setMethod = "set_carpet_mode",
        section = FeatureSection.CARPET,
        codec = FeatureCodec.CarpetModeEnable,
    ),
    BetaFeatureDef(
        id = "carpet_deep_clean",
        title = "Deep Carpet Cleaning", // verified
        description = "The robot performs cross-thatch cleaning on carpets (excluding mats) for enhanced cleaning results.\n*Cleaning will take longer.", // verified
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
        hiddenByBit = 119,
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
        title = "Auto Emptying", // verified
        description = "The robot will automatically empty after a cleanup.", // verified
        bit = null,
        getMethod = "get_dust_collection_switch_status",
        setMethod = "set_dust_collection_switch_status",
        section = FeatureSection.DOCK,
    ),
    // Bit 53 (AutoCollection2) turns the switch into this picker on the same RPCs (p223 L995261, L497750, L199459).
    BetaFeatureDef(
        id = "dust_collection_interval",
        title = "Dustbin Auto-Empty Frequency", // verified
        description = "Choose a suitable emptying frequency.", // verified
        bit = 53,
        getMethod = "get_dust_collection_switch_status",
        setMethod = "set_dust_collection_switch_status",
        section = FeatureSection.DOCK,
        choices = listOf(
            FeatureChoice(0, "Off", "No auto-empty after a cleanup. Empty the dustbin manually."), // verified
            FeatureChoice(1, "Balanced", "Automatically empties the dustbin after a cleanup. Suitable for a clean environment."), // verified
            FeatureChoice(2, "High", "Automatically returns to the dock to empty the dustbin during cleaning. Suitable for homes with pets or several carpets."), // verified
        ),
        replaces = "dust_collection_switch",
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
        // Official gate: ZoneReplenishCleanSwitch (154), outside Germany (p223 L208437).
        bit = 154,
        getMethod = "app_get_robot_setting",
        setMethod = "app_set_robot_setting",
        section = FeatureSection.FLOOR,
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
    BetaFeatureDefs.filter {
        (it.bit == null || newFeatureBit(newFeatureSet, it.bit)) &&
            (it.hiddenByBit == null || !newFeatureBit(newFeatureSet, it.hiddenByBit)) &&
            (!it.debugOnly || SHOW_DEBUG_FEATURES) &&
            (it.anyOfBits == null || it.anyOfBits.any { b -> newFeatureBit(newFeatureSet, b) })
    }.map { it.forRobot(newFeatureSet) }

// AI Environment Recognition also writes identify_room_status on robots with bit 66 (p223 L978725).
private fun BetaFeatureDef.forRobot(newFeatureSet: String?): BetaFeatureDef =
    if (codec is FeatureCodec.AiEnvironment) copy(codec = FeatureCodec.AiEnvironment(newFeatureBit(newFeatureSet, IDENTIFY_ROOM_BIT))) else this

/** Drops an older option once the option that replaces it has answered, so a failed read keeps the old one. */
fun List<BetaFeatureState>.withoutReplaced(): List<BetaFeatureState> {
    val replaced = mapNotNull { it.def.replaces }.toSet()
    return filter { it.def.id !in replaced }
}

/**
 * The order to read [defs] in: options gated on a feature bit first, then the ones every robot is
 * asked. A read stops at the first option with no reply, so a robot that ignores one of the
 * ungated commands then hides only the ungated options, not the ones its flags advertise.
 */
fun betaReadOrder(defs: List<BetaFeatureDef>): List<BetaFeatureDef> = defs.sortedBy { it.bit == null }

/** Whether the Beta page has anything to show: a flag-gated switch of its own, or flags with no switch here. */
fun showsBetaPage(defs: List<BetaFeatureDef>, unlisted: List<AdvertisedFeature>): Boolean =
    defs.any { it.section == FeatureSection.BETA && it.bit != null } || unlisted.isNotEmpty()

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
    122 to "Floor plan map",
    128 to "Roller mop",
    130 to "Forbid mop only",
    137 to "No mop, avoid stroll",
    145 to "Show dirty water tank clean state",
)

/**
 * Bits that have their own place in the app (SmartPlan, water slide, wash, empty and carpet modes), or that
 * name no setting: 42 is the get/set_clean_follow_ground_material_status pair listed above, 57 means the
 * robot has no carpet pressure setting, 38 is the cloud endpoint a robot uses, 52 only changes how the app reads
 * its AI recognition state from the camera, 67 is Matter (its own page), and 62 lowers the battery needed to start building a map from 20% to 15%.
 */
private val HandledElsewhereBits = setOf(35, 38, 41, 42, 47, 49, 51, 52, 53, 55, 57, 62, 67, 106) +
    AdvertisedFlagBits +
    // Camera (32, 33, 48, 73) and dock (107, 109) bits already read elsewhere.
    setOf(32, 33, 48, 73, 107, 109) +
    // App-only in the official app, or for hardware LocalRock has no page for (demo, arm, Wi-Fi setup).
    setOf(76, 86, 96, 99, 101, 110) +
    // 45 is covered by the water slide; 77 (room cap 31) is Manage Maps' maxRooms.
    setOf(45, 77) +
    // 74 is only the card bundle's dead OptimizeBattery entry; the a245 sets it and rejects the battery getter.
    setOf(74)

/** A `newFeatureSet` bit the robot advertises that has no switch here; unnamed bits read "Unknown (n)". */
data class AdvertisedFeature(val bit: Int, val name: String?)

/** What the robot advertises in `newFeatureSet` (bit 32 and up) that has no switch here, in bit order. */
fun unlistedAdvertisedFeatures(newFeatureSet: String?): List<AdvertisedFeature> {
    val s = newFeatureSet ?: return emptyList()
    val covered = BetaFeatureDefs.mapNotNull { it.bit }.toSet() + HandledElsewhereBits + VOICE_ASSISTANT_BIT
    return (32 until s.length * 4)
        .filter { it !in covered && newFeatureBit(s, it) }
        .map { AdvertisedFeature(it, advertisedFeatureName(it)) }
}

internal fun advertisedFeatureName(bit: Int): String = AdvertisedFeatureNames[bit] ?: "Unknown ($bit)"

/** Last read of one feature. [key] is the field name in the reply, null when the reply was a bare number. */
data class BetaFeatureState(
    val def: BetaFeatureDef,
    val value: Int? = null,
    val key: String? = null,
    val raw: String? = null,
    val answered: Boolean = true,
) {
    val enabled: Boolean get() = value == 1
    val toggleable: Boolean get() = if (def.choices != null) value != null else value == 0 || value == 1

    /** The label for the current value of a multi-choice setting; a value the app has no name for shows as "Other (n)". */
    val choiceLabel: String? get() = value?.let { v -> def.choices?.let { choiceLabelFor(it, v) } }

    /** Set when the switch cannot be turned on yet; the row shows it disabled with this tip. */
    val blockedReason: String? get() = if (enabled) null else featureBlockedReason(def, raw)
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
