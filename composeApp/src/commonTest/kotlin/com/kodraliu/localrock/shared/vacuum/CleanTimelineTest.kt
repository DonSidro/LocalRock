package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CleanTimelineTest {

    private fun page(raw: String): CleanTimelinePage =
        assertNotNull(CleanTimelineParser.parsePage(Json.parseToJsonElement(raw).jsonObject["result"]))

    private fun fullRun(): List<CleanTimelineStep> {
        val pages = CleanDetailFixtures.fullRunPages.map(::page)
        return CleanTimeline.build(pages.flatMap { it.events }, endEpoch = 1794596026L)
    }

    @Test
    fun pages_chain_through_from_plus_num() {
        val pages = CleanDetailFixtures.fullRunPages.map(::page)
        assertEquals(listOf(0, 48, 93, 139), pages.map { it.from })
        assertEquals(pages.drop(1).map { it.from }, pages.dropLast(1).map { it.nextFrom })
        assertEquals(listOf(true, true, true, false), pages.map { it.more })
        assertEquals(165, pages.sumOf { it.events.size })
    }

    @Test
    fun string_payloads_are_decoded_into_field_maps() {
        val first = page(CleanDetailFixtures.fullRunPages[0]).events[1]
        assertEquals(25, first.type)
        assertEquals(1, first.int(252))
        assertEquals(35, first.int(4))
    }

    @Test
    fun an_empty_reply_is_an_empty_page_and_garbage_is_not_a_page() {
        val empty = page(CleanDetailFixtures.EMPTY)
        assertTrue(empty.events.isEmpty())
        assertFalse(empty.more)
        assertNull(CleanTimelineParser.parsePage(null))
    }

    @Test
    fun full_run_starts_with_the_clean_settings() {
        val start = fullRun().first() as CleanTimelineStep.Start
        assertEquals(1, start.scope)
        assertEquals(CleanStartSettings(suction = 2, water = 35, route = 0, repeat = 1), start.settings)
        assertEquals("Start full cleaning", CleanTimelineLabels.title(start))
        assertEquals("Suction Standard · Water Level 15 · Route Standard", CleanTimelineLabels.detail(start))
    }

    @Test
    fun full_run_shows_the_error_with_its_sub_error_and_the_resume_after_it() {
        val steps = fullRun()
        val error = steps.filterIsInstance<CleanTimelineStep.Error>().single()
        assertEquals(1794589278L, error.time)
        assertEquals(5, error.code)
        assertEquals(1, error.subCode)
        assertEquals("An error occurs. (5-1)", CleanTimelineLabels.title(error))
        assertEquals("Main brush jammed", CleanTimelineLabels.detail(error))
        val resume = steps.first { it is CleanTimelineStep.Resume && it.time > error.time }
        assertEquals(1794589892L, resume.time)
        assertEquals("Resume", CleanTimelineLabels.title(resume))
    }

    @Test
    fun full_run_has_dock_dust_and_carpet_steps() {
        val steps = fullRun()
        assertEquals(4, steps.count { it is CleanTimelineStep.WashMop })
        assertEquals("Wash Mop", CleanTimelineLabels.title(steps.first { it is CleanTimelineStep.WashMop }))
        val empty = steps.filterIsInstance<CleanTimelineStep.AutoEmptying>()
        assertEquals(3, empty.size)
        assertEquals("Auto Emptying", CleanTimelineLabels.title(empty[0]))
        assertEquals("Max", CleanTimelineLabels.detail(empty[0]))
        val lifts = steps.filterIsInstance<CleanTimelineStep.MopLiftedForCarpet>()
        assertEquals(2, lifts.size)
        assertEquals("Lift up the mop cloth to clean carpet", CleanTimelineLabels.title(lifts[0]))
    }

    @Test
    fun rooms_use_the_map_name_then_the_room_type_then_the_segment_id() {
        val rooms = fullRun().filterIsInstance<CleanTimelineStep.Room>()
        assertEquals(2, rooms.first().segmentId)
        assertEquals("Clean Sunroom", CleanTimelineLabels.title(rooms.first()) { if (it == 2) "Sunroom" else null })
        assertEquals("Clean Kitchen", CleanTimelineLabels.title(rooms.first()))
        assertEquals("Clean Room 7", CleanTimelineLabels.title(CleanTimelineStep.Room(0L, 7, null)))
        assertTrue(rooms.none { it.segmentId > 30 })
    }

    @Test
    fun the_stain_re_mop_pass_comes_from_event_61_not_the_room_event() {
        val extra = fullRun().filterIsInstance<CleanTimelineStep.ExtraClean>().single()
        assertEquals(4, extra.reason)
        assertEquals("Heavy Dirt/Stain Recognition", CleanTimelineLabels.title(extra))
    }

    @Test
    fun finish_node_uses_the_record_end_and_is_last() {
        val steps = fullRun()
        assertEquals(CleanTimelineStep.Finished(1794596026L), steps.last())
        assertEquals("Cleaning Finished", CleanTimelineLabels.title(steps.last()))
        assertEquals(steps.map { it.time }, steps.map { it.time }.sorted())
    }

    @Test
    fun a_pause_shows_only_from_a_cleaning_state_and_a_start_error_alone_is_not_an_error_node() {
        val steps = CleanTimeline.build(page(CleanDetailFixtures.SHORT_RUN).events)
        assertEquals(1, steps.count { it is CleanTimelineStep.Pause })
        assertTrue(steps.none { it is CleanTimelineStep.Error })
        assertTrue(steps.none { it is CleanTimelineStep.Finished })
    }

    @Test
    fun error_label_without_a_sub_error_has_no_dash() {
        assertEquals("An error occurs. (38)", CleanTimelineLabels.title(CleanTimelineStep.Error(0L, 38, 0)))
        assertEquals("An error occurs.", CleanTimelineLabels.title(CleanTimelineStep.Error(0L, 0, 0)))
        assertEquals("5-1", CleanTimelineLabels.errorCodeText(5, 1))
    }
}
