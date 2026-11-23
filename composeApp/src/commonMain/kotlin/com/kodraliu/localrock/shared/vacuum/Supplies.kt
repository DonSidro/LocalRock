package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** The official Maintenance rows that have a counter, with the get_consumable key, life and unit. */
enum class Supply(val field: String?, val total: Long, val counted: Counted, val cleanable: Boolean = false) {
    MAIN_BRUSH("main_brush_work_time", 300L * 3600, Counted.SECONDS),
    SIDE_BRUSH("side_brush_work_time", 200L * 3600, Counted.SECONDS),
    FILTER("filter_work_time", 150L * 3600, Counted.SECONDS),
    SENSORS("sensor_dirty_time", 30L * 3600, Counted.SECONDS, cleanable = true),
    WATER_TANK_FILTER("filter_element_work_time", 100L * 3600, Counted.SECONDS),
    DIRTY_WATER_TANK("moproller_work_time", 60L * 3600, Counted.SECONDS, cleanable = true),
    CLEANING_TRAY("strainer_work_times", 150L, Counted.RUNS, cleanable = true),
    MAINTENANCE_BRUSH("cleaning_brush_work_times", 300L, Counted.RUNS),
    DUSTBIN("dust_collection_work_times", 90L, Counted.RUNS, cleanable = true),
    MOP(null, 0L, Counted.NONE),
    DUST_BAG(null, 0L, Counted.NONE),
    ROLLER_MOP_COVER(null, 0L, Counted.NONE),
    DIRTY_WATER_CHANNEL(null, 0L, Counted.NONE),
    ;

    enum class Counted { SECONDS, RUNS, NONE }

    /** What the robot has used since the last reset, or null when it does not report this item. */
    fun used(status: ConsumableStatus): Long? = when (this) {
        MAIN_BRUSH -> status.mainBrushWorkTime
        SIDE_BRUSH -> status.sideBrushWorkTime
        FILTER -> status.filterWorkTime
        SENSORS -> status.sensorDirtyTime
        WATER_TANK_FILTER -> status.filterElementWorkTime
        DIRTY_WATER_TANK -> status.moprollerWorkTime
        CLEANING_TRAY -> status.strainerWorkTime
        MAINTENANCE_BRUSH -> status.cleaningBrushWorkTime
        DUSTBIN -> status.dustCollectionWorkTimes
        MOP, DUST_BAG, ROLLER_MOP_COVER, DIRTY_WATER_CHANNEL -> null
    }

    /** The reset the official app sends: `reset_consumable [key]`. Null for rows without a counter. */
    fun resetRequest(): Pair<String, JsonArray>? = field?.let { "reset_consumable" to JsonArray(listOf(JsonPrimitive(it))) }
}

/** One row's numbers: percent left (0..100), and used / remaining in hours or runs. */
data class SupplyLife(val percent: Int, val used: Long, val remaining: Long)

fun supplyLife(supply: Supply, used: Long): SupplyLife {
    val percent = ConsumableStatus.percentLeft(used, supply.total)
    val left = (supply.total - used).coerceAtLeast(0L)
    return if (supply.counted == Supply.Counted.SECONDS) SupplyLife(percent, used / 3600, left / 3600) else SupplyLife(percent, used, left)
}

/** Official bar colours: above 20 normal, 5 to 20 warning, 5 and below error. */
enum class SupplyLevel { OK, LOW, OUT }

fun supplyLevel(percent: Int): SupplyLevel = when {
    percent > 20 -> SupplyLevel.OK
    percent > 5 -> SupplyLevel.LOW
    else -> SupplyLevel.OUT
}

// Rows in the official order (Supplies_Common_0/4/2 first). Water Tank Filter is a Tanos part (shown when its
// counter has run), the roller mop rows need the roller mop (isSupportRollerMop, bit 128), and Dustbin a collect-only dock.
fun visibleSupplies(status: ConsumableStatus, rollerMop: Boolean, collectOnlyDock: Boolean): List<Supply> = buildList {
    add(Supply.FILTER)
    add(Supply.MAIN_BRUSH)
    add(Supply.SIDE_BRUSH)
    add(Supply.SENSORS)
    if ((status.filterElementWorkTime ?: 0L) > 0L) add(Supply.WATER_TANK_FILTER)
    add(Supply.MOP)
    if (rollerMop) {
        add(Supply.ROLLER_MOP_COVER)
        add(Supply.DIRTY_WATER_CHANNEL)
        if (status.moprollerWorkTime != null) add(Supply.DIRTY_WATER_TANK)
    }
    if (status.strainerWorkTime != null) add(Supply.CLEANING_TRAY)
    if (status.cleaningBrushWorkTime != null) add(Supply.MAINTENANCE_BRUSH)
    if (collectOnlyDock && status.dustCollectionWorkTimes != null) add(Supply.DUSTBIN)
    add(Supply.DUST_BAG)
}
