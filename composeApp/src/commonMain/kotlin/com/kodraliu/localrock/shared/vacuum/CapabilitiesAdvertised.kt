package com.kodraliu.localrock.shared.vacuum

// Advertised-feature flags (B6) for other tracks' screens; each names its newFeatureSet bit.
data class AdvertisedCapabilities(
    val cleanThenMop: Boolean = false, // 93, and 114 on FCC robots: "Vac followed by Mop" mode tab (B2)
    val cleanThenMopRepeat: Boolean = false, // 102: send repeat:1 with the Vac followed by Mop write
    val cleanTimeline: Boolean = false, // 91: get_clean_detail answers, history timeline (B4)
    val mapEraser: Boolean = false, // 71: Manage Maps Remove tool, app_set_beautify_blocks (B3)
    val syncRoomNames: Boolean = false, // 98: sync_rooms_info + name_segment after a rename (B3/B1)
    val midwayDock: Boolean = false, // 85: mid-task Dock options, app_start_collect_dust / app_start_wash (B2)
    val dustCollectionCount: Boolean = false, // 100: record's dust_collection_status is a count (B4)
    val rollerMop: Boolean = false, // 128: roller mop and on-board dirty water tank row (B4)
    val dirtyTankCleanState: Boolean = false, // 145: dirty water tank clean state (B4)
    val cleanEfficiency: Boolean = false, // 108: Route picker Fast / Standard / Deep (B2)
    val forbidPureMop: Boolean = false, // 130: no mop-only option (B2)
    val noMopAvoidStroll: Boolean = false, // 137: vacuum-only fallback prompt before start (B2)
    val carpetShapes: Boolean = false, // 119: rectangle or circle carpets (B3)
    val floorPlanMap: Boolean = false, // 122: floor-plan background on the map (B3)
    val twoGearCollision: Boolean = false, // 118: Less Collision Mode is 1 or 2 only
)

/** Bits the flags above cover. */
internal val AdvertisedFlagBits = setOf(71, 85, 91, 93, 98, 100, 102, 108, 114, 118, 119, 122, 128, 130, 137, 145)

/** Official rule for Vac followed by Mop: bit 93, and on an FCC robot bit 114 too; an unknown FCC flag counts as FCC. */
internal fun cleanThenMopSupported(newFeatureSet: String?, fcc: Boolean?): Boolean =
    newFeatureBit(newFeatureSet, 93) && (fcc == false || newFeatureBit(newFeatureSet, 114))

// [fcc] is app_get_init_status local_info.featureset bit 0, null before the robot answers.
fun buildAdvertisedCapabilities(newFeatureSet: String?, fcc: Boolean? = null): AdvertisedCapabilities {
    fun bit(n: Int) = newFeatureBit(newFeatureSet, n)
    val ctm = cleanThenMopSupported(newFeatureSet, fcc)
    return AdvertisedCapabilities(
        cleanThenMop = ctm,
        cleanThenMopRepeat = ctm && bit(102),
        cleanTimeline = bit(91),
        mapEraser = bit(71),
        syncRoomNames = bit(98),
        midwayDock = bit(85),
        dustCollectionCount = bit(100),
        rollerMop = bit(128),
        dirtyTankCleanState = bit(145),
        cleanEfficiency = bit(108),
        forbidPureMop = bit(130),
        noMopAvoidStroll = bit(137),
        carpetShapes = bit(119),
        floorPlanMap = bit(122),
        twoGearCollision = bit(118),
    )
}
