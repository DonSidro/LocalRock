package com.kodraliu.localrock.shared.vacuum

// What one robot supports, as the settings screens ask it; screens test these flags, never bit numbers.
data class Capabilities(
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
    val matter: Boolean,
    val remoteControl: Boolean,
    val pinNGo: Boolean,
    val firmwareUpdates: Boolean,
    val dock: DockCapabilities,
    // Switches the robot reports, and the advertised bits with no control yet (ghost rows).
    val betaFeatures: List<BetaFeatureDef>,
    val unlistedFeatures: List<AdvertisedFeature>,
    val showsBetaFeatures: Boolean,
    val advertised: AdvertisedCapabilities = AdvertisedCapabilities(),
)

// Built from the model, the server's decimal featureSet and hex newFeatureSet, and the robot's own
// new_feature_info_str (robotFeatureSet), which wins once the robot has answered.
fun buildCapabilities(
    model: String?,
    featureSet: String?,
    serverFeatureSet: String?,
    robotFeatureSet: String?,
    robotIsFcc: Boolean? = null,
): Capabilities {
    val nfs = robotFeatureSet ?: serverFeatureSet
    val betaFeatures = supportedBetaFeatures(nfs)
    val unlisted = unlistedAdvertisedFeatures(nfs)
    val settings = buildSettingsCapabilities(featureSet, nfs)
    return Capabilities(
        hasCarpetSettings = settings.hasCarpetSettings,
        hasFloorSettings = settings.hasFloorSettings,
        hasFloorPattern = settings.hasFloorPattern,
        hasDockSettings = settings.hasDockSettings,
        hasAutoEmptySettings = settings.hasAutoEmptySettings,
        hasObstacleSettings = settings.hasObstacleSettings,
        chargingMode = settings.chargingMode,
        adjustedBattery = settings.adjustedBattery,
        offlineMap = settings.offlineMap,
        customDnd = settings.customDnd,
        multiSlotDnd = settings.multiSlotDnd,
        voiceAssistant = settings.voiceAssistant,
        childLock = settings.childLock,
        matter = isMatterSupported(nfs),
        remoteControl = true, // official gate is apiLevel >= 10003, which every LocalRock robot meets
        pinNGo = true,
        firmwareUpdates = true, // isSupportFirmwareUpdate is a host-app check; LocalRock always offers its server update
        dock = DockCapabilities(model, featureSet, nfs),
        betaFeatures = betaFeatures,
        unlistedFeatures = unlisted,
        showsBetaFeatures = showsBetaPage(betaFeatures, unlisted),
        advertised = buildAdvertisedCapabilities(nfs, robotIsFcc),
    )
}
