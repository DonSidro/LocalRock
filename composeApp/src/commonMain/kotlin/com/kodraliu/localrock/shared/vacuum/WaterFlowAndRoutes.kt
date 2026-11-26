package com.kodraliu.localrock.shared.vacuum

/** The official 1-30 Water Flow slider of water slide robots (NFS 106): water_box_mode = 220 + level. */
object WaterFlow {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 30
    /** PureWaterSuperBegin 248: from here the official slider shows the slipping warning. */
    const val WARNING_LEVEL = 28
    /** Levels that carry the 1, 2 and 3 droplet icons on the official track. */
    val DROPLET_LEVELS = listOf(5, 15, 25)
    private const val CODE_BASE = 220

    fun codeFor(level: Int): Int = CODE_BASE + level.coerceIn(MIN_LEVEL, MAX_LEVEL)

    /** The slider level for a reported code; the plugin's convertWaterMode maps 201/202/203/208 onto 225/235/245/250. */
    fun levelOf(code: Int?): Int? = when (code) {
        null -> null
        in (CODE_BASE + MIN_LEVEL)..(CODE_BASE + MAX_LEVEL) -> code - CODE_BASE
        WaterBoxMode.LOW -> 5
        WaterBoxMode.MEDIUM -> 15
        WaterBoxMode.HIGH -> 25
        208 -> MAX_LEVEL
        else -> null
    }

    fun band(level: Int): WaterFlowBand = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
        in 1..3 -> WaterFlowBand.SLIGHT
        in 4..7 -> WaterFlowBand.LOW
        in 8..12 -> WaterFlowBand.GENTLE
        in 13..17 -> WaterFlowBand.MEDIUM
        in 18..22 -> WaterFlowBand.MODERATE
        in 23..27 -> WaterFlowBand.HIGH
        else -> WaterFlowBand.EXTREME
    }

    fun showsWarning(level: Int): Boolean = level >= WARNING_LEVEL
}

/** Bands of getPureWaterModeText, keys mode_setting_panel_custom_water_box_7 (Slight) down to _1 (Extreme). */
enum class WaterFlowBand { SLIGHT, LOW, GENTLE, MEDIUM, MODERATE, HIGH, EXTREME }

/**
 * The Custom water slider of robots without water slide (isCustomWaterBoxDistanceSupported, RNF bit 31):
 * water_box_mode 207, then set_water_box_distance_off {"distance_off": d}. Lower d is wetter.
 * The slider runs 60..205 in steps of 5, both ends +25 on spin-mop models (p110 sliderWaterMin/Max).
 */
object CustomWater {
    const val MODE = 207
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 30
    private const val STEP = 5
    private const val BASE_MIN = 60
    private const val BASE_MAX = 205
    private const val SPIN_MOP_OFFSET = 25
    private const val SUPPORT_BIT = 31

    // Pearl, Vivian and CoralPro product families (p110 model configs); a101 measured live with distance_off 85 at level 30.
    private val OFFSET_MODELS = setOf(
        "a74", "a75", "a86", "a87", "a100", "a101", "a103", "a104", "a167", "a168", "a169", "a170",
        "a198", "a199", "a205", "a216", "a217", "a134", "a135", "a155", "a156", "a158", "a159", "a186",
        "a187", "a209", "a210", "a246", "a182", "a184", "a185", "a196", "a197", "a225", "a226",
        "a143", "a144", "a219", "a231", "a232", "a236",
    )

    fun isSupported(featureSet: String?): Boolean =
        featureSet?.trim()?.toULongOrNull()?.let { (it shr SUPPORT_BIT) and 1uL == 1uL } == true

    fun offsetFor(model: String?): Int = if (model?.substringAfterLast('.') in OFFSET_MODELS) SPIN_MOP_OFFSET else 0

    private fun total(offset: Int) = BASE_MIN + BASE_MAX + 2 * offset

    fun distanceFor(level: Int, offset: Int): Int =
        total(offset) - (BASE_MIN + offset + (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - 1) * STEP)

    /** The 1-30 slider number for a reported distance_off, or null when it is outside this model's range. */
    fun levelOf(distance: Int?, offset: Int): Int? {
        distance ?: return null
        val slider = total(offset) - distance
        val level = (slider - BASE_MIN - offset) / STEP + 1
        return level.takeIf { it in MIN_LEVEL..MAX_LEVEL }
    }

    /** getCustomWaterModeText: uneven bands on the distance with the offset removed. */
    fun band(distance: Int, offset: Int): WaterFlowBand? = when (distance - offset) {
        in 60..70 -> WaterFlowBand.EXTREME
        75 -> WaterFlowBand.HIGH
        in 80..125 -> WaterFlowBand.MODERATE
        130 -> WaterFlowBand.MEDIUM
        in 135..170 -> WaterFlowBand.GENTLE
        175 -> WaterFlowBand.LOW
        in 180..205 -> WaterFlowBand.SLIGHT
        else -> null
    }
}

/** The word a route shows: clean-efficiency robots (NFS 108) call 303 "Deep" and have no 301. */
enum class RouteName { FAST, STANDARD, DEEP, DEEP_PLUS }

data class RouteOption(val code: Int, val name: RouteName)

/** The plugin gates behind MopMethods and getCleanRouteItems. */
data class RouteGates(
    val cleanEfficiency: Boolean,
    val carefulSlow: Boolean,
    val fast: Boolean,
    val twoRoutesOnly: Boolean,
)

private const val CLEAN_EFFICIENCY_BIT = 108 // NFS CleanEfficiency
private const val CAREFUL_SLOW_RNF_BIT = 41 // robotNewFeatures, (n / 2^32) >> 9 & 1
private val TWO_ROUTE_MODELS = setOf("a72", "a84") // MF CleanMode_NonePureCleanMopWithMaxPlus

/**
 * Bit [bit] of the decimal robot feature number (new_feature_info, the server's featureSet); null when the
 * number is missing or has no high word, which some reports leave as a small placeholder.
 */
fun robotNewFeatureBit(featureSet: String?, bit: Int): Boolean? {
    val n = featureSet?.trim()?.toULongOrNull() ?: return null
    if (n < (1uL shl 32)) return null
    return (n shr bit) and 1uL == 1uL
}

fun routeGates(model: String?, featureSet: String?, newFeatureSet: String?): RouteGates = RouteGates(
    cleanEfficiency = newFeatureBit(newFeatureSet, CLEAN_EFFICIENCY_BIT),
    // Unknown counts as supported: every robot with a route picker in LocalRock so far has it.
    carefulSlow = robotNewFeatureBit(featureSet, CAREFUL_SLOW_RNF_BIT) ?: true,
    fast = isFastRouteSupported(newFeatureSet),
    twoRoutesOnly = model?.substringAfterLast('.') in TWO_ROUTE_MODELS,
)

/**
 * Routes the official mode sheet offers, in its order. Without clean efficiency, Deep and Deep+ show
 * only on the Mop tab (a Qrevo Pro switches suction off when sent 301 or 303).
 */
fun routeOptions(gates: RouteGates, mopOnly: Boolean): List<RouteOption> {
    val fast = RouteOption(MopRoute.FAST, RouteName.FAST)
    val standard = RouteOption(MopRoute.STANDARD, RouteName.STANDARD)
    if (gates.twoRoutesOnly) return listOf(fast, standard)
    if (!gates.cleanEfficiency && !mopOnly) return listOfNotNull(fast.takeIf { gates.fast }, standard)
    return buildList {
        if (gates.fast) add(fast)
        add(standard)
        if (!gates.cleanEfficiency) add(RouteOption(MopRoute.DEEP, RouteName.DEEP))
        if (gates.carefulSlow) add(RouteOption(MopRoute.DEEP_PLUS, if (gates.cleanEfficiency) RouteName.DEEP else RouteName.DEEP_PLUS))
    }
}
