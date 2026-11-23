package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CruiseRecordsTest {
    // Replies a Qrevo Curv 2 Flow gave to get_clean_record on 2026-11-13.
    private val cruise = """{"result":[{"begin":1794607499,"end":1794607757,"duration":211,"area":0,"error":0,"complete":1,"start_type":2,"clean_type":5,"finish_reason":114,"dust_collection_status":0,"avoid_count":0,"wash_count":0,"map_flag":0,"cleaned_area":0,"manual_replenish":0,"dirty_replenish":0,"clean_times":1,"task_id":1794607499,"extra_time":0,"sub_source":0,"general_info":""}]}"""
    private val pet = """{"result":[{"begin":1794541132,"end":1794542282,"duration":1150,"area":0,"error":0,"complete":1,"start_type":2,"clean_type":6,"finish_reason":116,"dust_collection_status":0,"avoid_count":0,"wash_count":0,"map_flag":0,"cleaned_area":0,"manual_replenish":0,"dirty_replenish":0,"clean_times":1,"task_id":1794541133,"extra_time":0,"sub_source":0,"general_info":""}]}"""
    private val petFailed = """{"result":[{"begin":1794433784,"end":1794433853,"duration":66,"area":0,"error":0,"complete":0,"start_type":2,"clean_type":6,"finish_reason":21,"dust_collection_status":0,"avoid_count":0,"wash_count":0,"map_flag":0,"cleaned_area":0,"manual_replenish":0,"dirty_replenish":0,"clean_times":1,"task_id":1794434111,"extra_time":0,"sub_source":0,"general_info":""}]}"""
    private val clean = """{"result":[{"begin":1794587027,"end":1794596026,"duration":6698,"area":92412500,"error":0,"complete":1,"start_type":2,"clean_type":1,"finish_reason":52,"dust_collection_status":4,"avoid_count":254,"wash_count":4,"map_flag":0,"cleaned_area":93592500,"manual_replenish":0,"dirty_replenish":0,"clean_times":1,"task_id":0,"extra_time":0,"sub_source":0,"general_info":""}]}"""

    private fun parse(reply: String, id: Long): CleanRecord {
        val result = Json.parseToJsonElement(reply).let { (it as kotlinx.serialization.json.JsonObject)["result"]!! }
        return assertNotNull(parseCleanRecord(result, id))
    }

    @Test
    fun cruiseRunIsTypeFiveWithItsTitleAndDuration() {
        val r = parse(cruise, 1794607499)
        assertTrue(r.isCruise)
        assertEquals("Cruise", r.cruiseTitle)
        assertEquals("4 min", cleanDurationLabel(r.durationSeconds))
        assertEquals(1, r.complete)
        assertEquals(1794607499, r.beginEpoch)
    }

    @Test
    fun petSearchRunHasItsTitle() {
        val r = parse(pet, 1794541132)
        assertTrue(r.isCruise)
        assertEquals("Search for the Pet", r.cruiseTitle)
        assertEquals("19 min", cleanDurationLabel(r.durationSeconds))
    }

    @Test
    fun failedPetSearchIsIncompleteWithReasonTwentyOne() {
        val r = parse(petFailed, 1794433784)
        assertTrue(r.isCruise)
        assertEquals(0, r.complete)
        assertEquals(21, r.finishReason)
        assertEquals("1 min", cleanDurationLabel(r.durationSeconds))
    }

    @Test
    fun normalCleanStaysOutOfTheCruiseList() {
        val records = listOf(parse(clean, 1794587027), parse(petFailed, 1794433784), parse(cruise, 1794607499), parse(pet, 1794541132))
        assertFalse(records[0].isCruise)
        assertEquals(listOf(1794607499L, 1794541132L, 1794433784L), records.cruiseRecords().map { it.beginEpoch })
        assertEquals(listOf(1794587027L), records.cleaningRecords().map { it.beginEpoch })
    }
}
