package com.kodraliu.localrock.shared.vacuum

// Manage Maps gates (track B3), from the plugin's feature getters; eraser (71) and floor plan (122) live in Capabilities.advertised.
internal const val MAX_ZONE_OPENED_FEATURE_BIT = 77 // NewFeatureStrBit.MaxZoneOpened, isAllowMaxBlock
internal const val BACKUP_MAP_FEATURE_BIT = 49 // isSupportBackupMap, bit 49 of the decimal new_feature_info

/** Room limit for Divide: 31 with MaxZoneOpened, else 15. */
internal const val MAX_ROOMS = 15
internal const val MAX_ROOMS_OPENED = 31

internal data class MapCapabilities(
    val mapTools: Set<MapTool>,
    val mapBackup: Boolean,
    val maxRooms: Int,
)

// Tools LocalRock can edit on every legacy map today; Furniture and Sequence stay off until they exist.
private val BaseMapTools = listOf(
    MapTool.DIVIDE, MapTool.COMBINE, MapTool.NAME_ROOM, MapTool.NO_GO, MapTool.THRESHOLD, MapTool.FLOOR_TYPE, MapTool.CARPET,
)

/** [featureSet] is the decimal new_feature_info, [newFeatureSet] the hex new_feature_info_str. */
internal fun buildMapCapabilities(
    featureSet: String?,
    newFeatureSet: String?,
    advertised: AdvertisedCapabilities = buildAdvertisedCapabilities(newFeatureSet),
): MapCapabilities {
    val eraser = advertised.mapEraser
    val rnf = featureSet?.toULongOrNull() ?: 0uL
    return MapCapabilities(
        mapTools = MapTool.entries.filter { it in BaseMapTools || (it == MapTool.REMOVE && eraser) }.toSet(),
        mapBackup = (rnf shr BACKUP_MAP_FEATURE_BIT) and 1uL == 1uL,
        maxRooms = if (newFeatureBit(newFeatureSet, MAX_ZONE_OPENED_FEATURE_BIT)) MAX_ROOMS_OPENED else MAX_ROOMS,
    )
}
