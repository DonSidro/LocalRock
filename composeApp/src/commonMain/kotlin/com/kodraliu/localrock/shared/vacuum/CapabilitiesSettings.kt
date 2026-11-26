package com.kodraliu.localrock.shared.vacuum

// Settings flags (track B1). NFS = new_feature_info_str bit, RNF = decimal new_feature_info bit, as the plugin gates them.
internal data class SettingsCapabilities(
    val hasCarpetSettings: Boolean,
    val hasFloorSettings: Boolean,
    val hasFloorPattern: Boolean,
    val hasDockSettings: Boolean,
    val hasAutoEmptySettings: Boolean,
    val hasObstacleSettings: Boolean,
    val chargingMode: Boolean,
    val adjustedBattery: Boolean,
    val offlineMap: Boolean,
    val customDnd: Boolean,
    val multiSlotDnd: Boolean,
    val voiceAssistant: Boolean,
    val childLock: Boolean,
)

/** One bit of the decimal `new_feature_info` (the server's featureSet). */
internal fun robotFeatureBit(featureSet: String?, bit: Int): Boolean =
    ((featureSet?.toULongOrNull() ?: 0uL) shr bit) and 1uL == 1uL

internal fun buildSettingsCapabilities(featureSet: String?, newFeatureSet: String?): SettingsCapabilities {
    fun nfs(n: Int) = newFeatureBit(newFeatureSet, n)
    fun rnf(n: Int) = robotFeatureBit(featureSet, n)
    return SettingsCapabilities(
        // shouldShowCarpetSettingView only hides the row on TanosS Lite and FCC models without carpet.
        hasCarpetSettings = true,
        hasFloorSettings = nfs(11) || nfs(34),
        hasFloorPattern = nfs(70),
        // isOldBelowO3 and isPureWash need the plugin's dock tables, which LocalRock does not carry yet.
        hasDockSettings = true,
        // Dock.isPureCollect needs the dock type table; no collect-only dock has been seen yet.
        hasAutoEmptySettings = false,
        // MF.AIRecognition_Setting is a per-model flag; the root also shows the page when it has rows.
        hasObstacleSettings = false,
        chargingMode = rnf(45),
        // OptimizeBattery is NFS 72 in the plugin; both test robots leave it clear and reject get_optimize_battery_status.
        adjustedBattery = nfs(72),
        offlineMap = nfs(14),
        customDnd = nfs(2),
        multiSlotDnd = nfs(140),
        voiceAssistant = nfs(37),
        childLock = rnf(8),
    )
}
