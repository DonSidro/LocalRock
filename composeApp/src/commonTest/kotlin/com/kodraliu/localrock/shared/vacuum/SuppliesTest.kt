package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuppliesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // get_consumable replies read on 2026-11-13.
    private val curvReply = """{"result":[{"main_brush_work_time":5950,"side_brush_work_time":7964,"filter_work_time":5950,"filter_element_work_time":0,"sensor_dirty_time":6700,"moproller_work_time":5455,"strainer_work_times":4,"dust_collection_work_times":4}]}"""
    private val proReply = """{"result":[{"main_brush_work_time":658265,"side_brush_work_time":658265,"filter_work_time":3140,"filter_element_work_time":0,"sensor_dirty_time":3140,"strainer_work_times":66,"dust_collection_work_times":875}]}"""

    private fun parse(reply: String): ConsumableStatus =
        json.decodeFromJsonElement(ConsumableStatus.serializer(), json.parseToJsonElement(reply).jsonObject["result"]!!.jsonArray[0])

    @Test
    fun theNewCountersAreRead() {
        val curv = parse(curvReply)
        assertEquals(0L, curv.filterElementWorkTime)
        assertEquals(5455L, curv.moprollerWorkTime)
        assertEquals(4L, curv.dustCollectionWorkTimes)
        assertNull(parse(proReply).moprollerWorkTime)
    }

    @Test
    fun rowsFollowTheOfficialGates() {
        assertEquals(
            listOf(Supply.FILTER, Supply.MAIN_BRUSH, Supply.SIDE_BRUSH, Supply.SENSORS, Supply.MOP, Supply.ROLLER_MOP_COVER, Supply.DIRTY_WATER_CHANNEL, Supply.DIRTY_WATER_TANK, Supply.CLEANING_TRAY, Supply.DUST_BAG),
            visibleSupplies(parse(curvReply), rollerMop = true, collectOnlyDock = false),
        )
        assertEquals(
            listOf(Supply.FILTER, Supply.MAIN_BRUSH, Supply.SIDE_BRUSH, Supply.SENSORS, Supply.MOP, Supply.CLEANING_TRAY, Supply.DUST_BAG),
            visibleSupplies(parse(proReply), rollerMop = false, collectOnlyDock = false),
        )
        val tanos = parse(proReply).copy(filterElementWorkTime = 3600)
        val rows = visibleSupplies(tanos, rollerMop = false, collectOnlyDock = true)
        assertEquals(true, Supply.WATER_TANK_FILTER in rows)
        assertEquals(true, Supply.DUSTBIN in rows)
    }

    @Test
    fun lifeIsInHoursForTimersAndRunsForCounters() {
        assertEquals(SupplyLife(percent = 100, used = 1, remaining = 298), supplyLife(Supply.MAIN_BRUSH, 5950))
        assertEquals(SupplyLife(percent = 98, used = 1, remaining = 58), supplyLife(Supply.DIRTY_WATER_TANK, 5455))
        assertEquals(SupplyLife(percent = 0, used = 875, remaining = 0), supplyLife(Supply.DUSTBIN, 875))
        assertEquals(SupplyLife(percent = 98, used = 4, remaining = 146), supplyLife(Supply.CLEANING_TRAY, 4))
    }

    @Test
    fun levelsUseTheOfficialThresholds() {
        assertEquals(SupplyLevel.OK, supplyLevel(21))
        assertEquals(SupplyLevel.LOW, supplyLevel(20))
        assertEquals(SupplyLevel.LOW, supplyLevel(6))
        assertEquals(SupplyLevel.OUT, supplyLevel(5))
    }

    @Test
    fun resetSendsTheConsumableKeyOnly() {
        assertEquals("reset_consumable" to JsonArray(listOf(JsonPrimitive("moproller_work_time"))), Supply.DIRTY_WATER_TANK.resetRequest())
        assertEquals("reset_consumable" to JsonArray(listOf(JsonPrimitive("main_brush_work_time"))), Supply.MAIN_BRUSH.resetRequest())
        assertNull(Supply.MOP.resetRequest())
        assertEquals(Supply.entries.mapNotNull { it.field }.toSet().size, Supply.entries.count { it.field != null })
    }
}
