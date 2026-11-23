package com.kodraliu.localrock.shared.vacuum

// Device page flags (track B2); clean-then-mop, pure mop and midway docking come from Capabilities.advertised.
private const val DYNAMIC_SKIP_ZONE_BIT = 25 // isDynamiclySkipCleanZoneSupported, low word 0x2000000
private const val DYNAMIC_ADD_ZONES_BIT = 26 // isDynamiclyAddCleanZonesSupported, low word 0x4000000

data class DevicePageFlags(
    // Official isSupportMainPageSmartCommand: both dynamic-zone bits; its app apiLevel and scene checks hold for LocalRock.
    val routineTabKnown: Boolean,
)

// Official isSupportMainPageSmartCommand: both dynamic-zone bits (p223 L207364).
fun devicePageFlags(newFeatureSet: String?): DevicePageFlags = DevicePageFlags(
    routineTabKnown = newFeatureBit(newFeatureSet, DYNAMIC_SKIP_ZONE_BIT) && newFeatureBit(newFeatureSet, DYNAMIC_ADD_ZONES_BIT),
)

/** Device page tabs: Routine shows when the official gate is known true or, failing that, when routines exist. */
fun visibleMainTabs(capabilities: Capabilities, routineCount: Int): List<MainTab> =
    if (capabilities.hasRoutineTab || routineCount > 0) listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE, MainTab.ROUTINE)
    else listOf(MainTab.FULL, MainTab.ROOM, MainTab.ZONE)
