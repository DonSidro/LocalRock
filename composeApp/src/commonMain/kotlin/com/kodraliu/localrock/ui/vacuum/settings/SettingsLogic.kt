package com.kodraliu.localrock.ui.vacuum.settings

import com.kodraliu.localrock.ui.vacuum.LaunchedOnEntry
import com.kodraliu.localrock.ui.vacuum.VacuumViewModel

import com.kodraliu.localrock.shared.vacuum.BetaFeatureDef
import com.kodraliu.localrock.shared.vacuum.BetaFeatureState
import com.kodraliu.localrock.shared.vacuum.CleanRecord
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.DockChoice
import com.kodraliu.localrock.shared.vacuum.DockOptions
import com.kodraliu.localrock.shared.vacuum.VacuumStateCodes
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.inSection
import com.kodraliu.localrock.ui.strings.SettingsStrings
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.isoDayNumber

internal fun clockText(hour: Int, minute: Int) = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** The earliest enabled schedule still ahead today, as hour and minute; null when none is left today. */
internal fun nextCleaningToday(timers: List<CleanTimer>, now: LocalDateTime): Pair<Int, Int>? {
    val today = now.dayOfWeek.isoDayNumber % 7 // CleanTimer weekdays: 0 = Sunday
    val nowMinutes = now.hour * 60 + now.minute
    return timers.asSequence()
        .filter { it.enabled }
        .filter { t -> t.weekdays.isEmpty() || today in t.weekdays }
        .filter { it.hour * 60 + it.minute > nowMinutes }
        .minByOrNull { it.hour * 60 + it.minute }
        ?.let { it.hour to it.minute }
}

internal fun schedulesDetail(next: Pair<Int, Int>?): String =
    next?.let { (h, m) -> SettingsStrings.SCHEDULES_NEXT.replace("{time}", clockText(h, m)) } ?: SettingsStrings.SCHEDULES_DETAIL

/** True when any supply the robot reports has no life left, the official red-dot case. */
internal fun suppliesDue(status: ConsumableStatus?): Boolean {
    val s = status ?: return false
    fun due(used: Long?, total: Long) = used != null && ConsumableStatus.percentLeft(used, total) == 0
    return due(s.mainBrushWorkTime, ConsumableStatus.MAIN_BRUSH_LIFETIME_S) ||
        due(s.sideBrushWorkTime, ConsumableStatus.SIDE_BRUSH_LIFETIME_S) ||
        due(s.filterWorkTime, ConsumableStatus.FILTER_LIFETIME_S) ||
        due(s.sensorDirtyTime, ConsumableStatus.SENSOR_LIFETIME_S) ||
        due(s.cleaningBrushWorkTime, ConsumableStatus.CLEANING_BRUSH_LIFETIME_RUNS) ||
        due(s.strainerWorkTime, ConsumableStatus.STRAINER_LIFETIME_RUNS)
}

internal fun maintenanceDetail(status: ConsumableStatus?): String =
    if (suppliesDue(status)) SettingsStrings.MAINTENANCE_DUE else SettingsStrings.MAINTENANCE_NORMAL

/** The Robot Voice row subtitle: the volume level when known. */
internal fun robotVoiceDetail(volume: Int?): String =
    volume?.let { SettingsStrings.ROBOT_VOICE_DETAIL_LEVEL.replace("{level}", it.coerceIn(0, 100).toString()) }
        ?: SettingsStrings.ROBOT_VOICE_DETAIL

/** The newest run, which the header card sums up. */
internal fun lastCleanRecord(records: List<CleanRecord>): CleanRecord? = records.maxByOrNull { it.beginEpoch }

/** Duration in whole minutes and area in m2, the way the header card shows them. */
internal fun lastCleanFigures(record: CleanRecord?): Pair<String, String> {
    if (record == null) return SettingsStrings.NO_VALUE to SettingsStrings.NO_VALUE
    val minutes = (record.durationSeconds + 59) / 60
    val m2 = (record.cleanedAreaMm2 ?: record.areaMm2) / 1_000_000.0
    val area = ((m2 * 10).toLong() / 10.0).let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
    return "$minutes min" to "$area m²"
}

/** One choice on the Carpet Cleaning Setting page, with its set_carpet_clean_mode value. */
internal data class CarpetModeCard(val value: Int, val title: String, val detail: String)

// Wire values 0 Avoid, 1 Rise, 2 Vac & Mop (the plugin's "Ignore"), 3 Dynamic Lift; Rise is labelled Vacuum Only.
internal fun carpetModeCards(current: Int?, crossSupported: Boolean = false): List<CarpetModeCard> = buildList {
    add(CarpetModeCard(1, SettingsStrings.CARPET_VACUUM_ONLY, SettingsStrings.CARPET_VACUUM_ONLY_DETAIL))
    add(CarpetModeCard(0, SettingsStrings.CARPET_AVOID, SettingsStrings.CARPET_AVOID_DETAIL))
    if (crossSupported) add(CarpetModeCard(CARPET_CROSS_CARD, SettingsStrings.CARPET_CROSS, SettingsStrings.CARPET_CROSS_DETAIL))
    add(CarpetModeCard(2, SettingsStrings.CARPET_VAC_AND_MOP, SettingsStrings.CARPET_VAC_AND_MOP_DETAIL))
    if (current == 3) add(CarpetModeCard(3, SettingsStrings.CARPET_DYNAMIC_LIFT, SettingsStrings.CARPET_DYNAMIC_LIFT_DETAIL))
}

/** One radio row on a dock settings page: the value the robot takes, its name and the official description. */
internal data class DockOptionCard(val value: Int, val title: String, val detail: String?)

// Super Deep (8 without the soak bit) has no known official description.
internal fun washModeCards(modes: List<Pair<Int, String>>): List<DockOptionCard> = modes.map { (code, label) ->
    val detail = when {
        code == 10 -> SettingsStrings.WASH_SMART_DETAIL
        code == 0 -> SettingsStrings.WASH_LIGHT_DETAIL
        code == 1 -> SettingsStrings.WASH_BALANCED_DETAIL
        code == 2 -> SettingsStrings.WASH_DEEP_DETAIL
        code == 8 && label == "Soak" -> SettingsStrings.WASH_SOAK_DETAIL
        else -> null
    }
    DockOptionCard(code, label, detail)
}

internal fun washTemperatureCards(dockType: Int?): List<DockOptionCard> = DockOptions.washWaterTemperature(dockType).map {
    val detail = when (it.label) {
        "Room Temperature" -> SettingsStrings.WASH_ROOM_TEMPERATURE_DETAIL
        "High Temperature" -> SettingsStrings.WASH_HIGH_TEMPERATURE_DETAIL
        else -> null
    }
    DockOptionCard(it.code, it.label, detail)
}

// The first three options are Eco, Standard and Deep; a fourth is an unnamed length the robot reported.
internal fun dryTimeCards(options: List<DockChoice>): List<DockOptionCard> = options.mapIndexed { i, option ->
    val detail = listOf(SettingsStrings.DRY_ECO_DETAIL, SettingsStrings.DRY_STANDARD_DETAIL, SettingsStrings.DRY_DEEP_DETAIL).getOrNull(i)
    DockOptionCard(option.code, option.label, detail)
}

// Cross is switch_status bit 5, not a carpet_clean_mode value, so its card has its own key.
internal const val CARPET_CROSS_CARD = -1

/** The selected card: Cross whenever its bit is on, as the plugin's selectedMode getter does, else the clean mode. */
internal fun selectedCarpetCard(mode: Int?, crossOn: Boolean): Int? = if (crossOn) CARPET_CROSS_CARD else mode

/** One write a carpet card sends. */
internal sealed interface CarpetWrite {
    data class CleanMode(val mode: Int) : CarpetWrite
    data class Cross(val on: Boolean) : CarpetWrite
}

// As the plugin's saveGlobalCarpetMode: Cross sends only Cross on; other cards set the mode, then Cross off if supported.
internal fun carpetCardWrites(card: Int, crossSupported: Boolean): List<CarpetWrite> =
    if (card == CARPET_CROSS_CARD) listOf(CarpetWrite.Cross(true))
    else listOfNotNull(CarpetWrite.CleanMode(card), CarpetWrite.Cross(false).takeIf { crossSupported })

/** The four titled groups of the official Dock Settings page, plus OTHER for rows it has no place for yet. */
internal enum class DockGroup { WASH, EMPTY, DRY, MAINTENANCE, OTHER }

// By the plugin's DockSettingPage order: auto-delivery 112, autonomous wash 134, hot wash 41; UVC 84 and dry timer 83.
internal fun dockGroupOf(def: BetaFeatureDef): DockGroup = when {
    def.id == "dust_collection_switch" || def.id == "dust_collection_interval" -> DockGroup.EMPTY
    def.bit in setOf(41, 112, 134) -> DockGroup.WASH
    def.bit in setOf(83, 84) -> DockGroup.DRY
    def.bit == 39 -> DockGroup.MAINTENANCE
    else -> DockGroup.OTHER
}

/** The dock rows the robot's reads put on Dock Settings, in official order within each group. */
internal data class DockPageRows(
    val wash: List<BetaFeatureState>,
    val empty: List<BetaFeatureState>,
    val dryingStartMethod: BetaFeatureState?,
    val autoDryingSwitch: Boolean,
    val dryOther: List<BetaFeatureState>,
    val maintenance: List<BetaFeatureState>,
    val other: List<BetaFeatureState>,
)

// Official gates (p223 L994860, L995290-995420): the fluid switch needs dss fluid bits, the dryer switch hides behind bit 83.
internal fun dockPageRows(features: List<BetaFeatureState>, mopDrying: Boolean?, dss: Int?): DockPageRows {
    val by = features.inSection(FeatureSection.DOCK)
        .filter { it.def.id != "clean_fluid_delivery" || DockOptions.cleanFluidReported(dss) }
        .groupBy { dockGroupOf(it.def) }
    val dry = by[DockGroup.DRY].orEmpty()
    val startMethod = dry.firstOrNull { it.def.bit == DRY_INTERVAL_TIMER_BIT }
    return DockPageRows(
        wash = by[DockGroup.WASH].orEmpty(),
        empty = by[DockGroup.EMPTY].orEmpty(),
        dryingStartMethod = startMethod,
        autoDryingSwitch = mopDrying != null && startMethod == null,
        dryOther = dry.filter { it !== startMethod },
        maintenance = by[DockGroup.MAINTENANCE].orEmpty(),
        other = by[DockGroup.OTHER].orEmpty(),
    )
}

private const val DRY_INTERVAL_TIMER_BIT = 83

// States in which the official app first asks to end the task (RSM.isRunning, p223 L1298910); the set is inferred.
private val taskStates = setOf(
    VacuumStateCodes.CLEANING, VacuumStateCodes.RETURNING_HOME, VacuumStateCodes.SPOT_CLEANING,
    VacuumStateCodes.GOING_TO_TARGET, VacuumStateCodes.ZONED_CLEANING, VacuumStateCodes.SEGMENT_CLEANING,
    VacuumStateCodes.GOING_TO_WASH_MOP, VacuumStateCodes.MAPPING, VacuumStateCodes.PATROLLING,
)

/** True when Start Self-Cleaning must end the robot's current task first. */
internal fun traySelfCleanEndsTask(state: Int?): Boolean = state in taskStates

// Floor Cleaning Setting groups in official order, by feature bit; 42 is the floor-direction pair.
private val FloorGroupBits = listOf(
    listOf(11, 42, 70),
    listOf(80),
    listOf(79, 116),
    listOf(54, 40, 63),
    listOf(34, 56),
    listOf(43),
    listOf(131, 154),
)
private val FloorGroupIds = mapOf(
    "identify_ground_material" to 0,
    "follow_ground_material" to 0,
    "cleaning_pattern" to 0,
    "uncleaned_area_reminder" to 6,
)

/** The robot's floor rows split into the official groups, each in official order; unknown rows form a last group. */
internal fun floorFeatureGroups(features: List<BetaFeatureState>): List<List<BetaFeatureState>> {
    fun place(def: BetaFeatureDef): Pair<Int, Int> {
        FloorGroupBits.forEachIndexed { g, bits -> bits.indexOf(def.bit).takeIf { it >= 0 }?.let { return g to it } }
        FloorGroupIds[def.id]?.let { return it to Int.MAX_VALUE }
        return FloorGroupBits.size to Int.MAX_VALUE
    }
    val floor = features.inSection(FeatureSection.FLOOR)
    return floor.withIndex()
        .groupBy { place(it.value.def).first }
        .entries
        .sortedBy { it.key }
        .map { (_, group) -> group.sortedWith(compareBy({ place(it.value.def).second }, { it.index })).map { it.value } }
}

// Reactive Obstacle Avoidance groups in official order (p223 L979330-979960), by feature id.
private val ObstacleGroupIds = listOf(
    listOf("obstacle_avoidance", "obstacle_photos"),
    listOf("avoid_collision", "less_collision_mode"),
    listOf("pet_mode", "pet_snapshot"),
    listOf("ai_environment"),
    listOf("fill_light"),
)

/** The robot's obstacle rows split into the official groups, each in official order; unknown rows form a last group. */
internal fun obstacleFeatureGroups(features: List<BetaFeatureState>): List<List<BetaFeatureState>> {
    fun place(id: String): Pair<Int, Int> {
        ObstacleGroupIds.forEachIndexed { g, ids -> ids.indexOf(id).takeIf { it >= 0 }?.let { return g to it } }
        return ObstacleGroupIds.size to Int.MAX_VALUE
    }
    return features.inSection(FeatureSection.OBSTACLE).withIndex()
        .groupBy { place(it.value.def.id).first }
        .entries
        .sortedBy { it.key }
        .map { (_, group) -> group.sortedWith(compareBy({ place(it.value.def.id).second }, { it.index })).map { it.value } }
}

/** Where a ROBOT-section feature sits under More Options and Robot Voice; VOICE_SWITCH goes above the volume. */
internal enum class RobotRowPlace { LIGHTS, ADJUSTED_BATTERY, VOICE_SWITCH, VOICE, OTHER }

internal fun robotRowPlace(def: BetaFeatureDef): RobotRowPlace = when (def.id) {
    "voice_control_led" -> RobotRowPlace.LIGHTS
    "optimize_battery" -> RobotRowPlace.ADJUSTED_BATTERY
    "voice_assistant" -> RobotRowPlace.VOICE_SWITCH
    "full_duplex" -> RobotRowPlace.VOICE
    else -> RobotRowPlace.OTHER
}

internal fun List<BetaFeatureState>.robotRows(place: RobotRowPlace): List<BetaFeatureState> =
    inSection(FeatureSection.ROBOT).filter { robotRowPlace(it.def) == place }
