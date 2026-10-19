package com.kodraliu.localrock.shared.vacuum

data class WashFrequencyOption(val label: String, val mode: Int, val intervalMinutes: Int? = null)

// Official plugin: FM.Dock.isOldWashFrequency, DockConfigs and getWashModes.
private val legacyWashModels = setOf(
    "a26", "a27", "a29", "a30", "a46", "a47", "a62", "a64", "a65", "a66",
    "a68", "a69", "a70", "a76",
)
private val simpleWashDockTypes = setOf(41, 42, 43, 46)
// a187 plugin DockConfigs, lines 281000–281788; charging and collection-only types excluded.
private val knownWashDockTypes = setOf(2, 3) + (6..11) + (13..24) + (26..35) + (37..46)

class DockCapabilities(model: String?, featureSet: String?, newFeatureSet: String?) {
    private val smartWash = newFeatureBit(newFeatureSet, 34) || newFeatureBit(newFeatureSet, 150)
    private val superDeepWash = newFeatureBit(newFeatureSet, 15)
    private val soakWash = newFeatureBit(newFeatureSet, 107)
    private val aiWash = newFeatureBit(newFeatureSet, 144)

    val washIntervals: List<Int> = if (model?.substringAfterLast('.') in legacyWashModels) {
        (10..50 step 5).toList()
    } else {
        listOf(10, 15, 20, 25)
    }

    val washFrequencyOptions: List<WashFrequencyOption> = buildList {
        washIntervals.forEach { add(WashFrequencyOption("$it min", 0, it)) }
        add(WashFrequencyOption("By room", 1))
        if (newFeatureBit(newFeatureSet, 109)) add(WashFrequencyOption("Smart", 2))
    }

    // Dust mode support is bit 25 of decimal featureSet, not the hex newFeatureSet.
    val autoEmptyModes: List<Pair<Int, String>> = if (
        (featureSet?.toULongOrNull() ?: 0uL) and (1uL shl 25) != 0uL
    ) {
        listOf(0 to "Smart", 1 to "Light", 2 to "Balanced", 4 to "Max")
    } else {
        emptyList()
    }

    fun washModes(dockType: Int?, dss: Int?): List<Pair<Int, String>> {
        if (dockType !in knownWashDockTypes) return listOf(0 to "Light")
        val simple = dockType in simpleWashDockTypes
        return buildList {
            if (smartWash) add(10 to "Smart")
            add(0 to "Light")
            if (!simple || aiWash) add(1 to "Balanced")
            if (!simple) add(2 to "Deep")
            // dss low bits report the plumbing module; a dock type alone cannot establish it.
            if (superDeepWash && (soakWash || (dss?.and(3) == 2 && !smartWash))) {
                add(8 to if (soakWash) "Soak" else "Super Deep")
            }
        }
    }

    fun washModeCode(raw: Int?, dockType: Int?): Int? = when {
        dockType !in simpleWashDockTypes -> raw
        raw == 2 -> 8
        raw == 1 && !aiWash -> 10
        else -> raw
    }

    fun washModeLabel(raw: Int?, dockType: Int?): String = when (val mode = washModeCode(raw, dockType)) {
        null -> "Unavailable"
        0 -> "Light"
        1 -> "Balanced"
        2 -> "Deep"
        8 -> if (soakWash) "Soak" else "Super Deep"
        10 -> "Smart"
        else -> "Unknown ($mode)"
    }

    fun autoEmptyModeLabel(mode: Int?): String = when (mode) {
        null -> "Unavailable"
        0 -> "Smart"
        1 -> "Light"
        2 -> "Balanced"
        3 -> "Strong"
        4 -> "Max"
        else -> "Unknown ($mode)"
    }
}
