package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.model.LastKnown
import com.kodraliu.localrock.shared.model.LastKnownPoint
import com.kodraliu.localrock.shared.model.LastKnownPosition
import com.kodraliu.localrock.shared.model.LastKnownStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfflineSnapshotTest {
    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-10-22T03:00:00Z")

    @Test
    fun a_time_today_shows_only_the_clock() {
        assertEquals("12:34 AM", formatLastKnownTime("2026-10-22T00:34:11.654321+00:00", now, utc))
        assertEquals("1:05 PM", formatLastKnownTime("2026-10-22T13:05:00Z", now, utc))
    }

    @Test
    fun an_earlier_day_shows_the_date_too() {
        assertEquals("Oct 21, 8:34 PM", formatLastKnownTime("2026-10-21T20:34:00Z", now, utc))
    }

    @Test
    fun missing_or_bad_timestamps_are_null() {
        assertNull(formatLastKnownTime(null, now, utc))
        assertNull(formatLastKnownTime("yesterday", now, utc))
    }

    @Test
    fun offline_robot_lines_say_when_battery_and_where() {
        val lastKnown = LastKnown(
            known = true,
            offlineAt = "2026-10-22T00:34:11Z",
            lastSeenAt = "2026-10-22T00:33:57Z",
            status = LastKnownStatus(state = 2, battery = 5),
            position = LastKnownPosition(robot = LastKnownPoint(24343, 38108, 170)),
        )

        assertEquals(
            listOf("Offline since 12:34 AM", "Last reported: Battery 5%, Charger disconnected", "Showing where it was last seen"),
            lastKnownLines(lastKnown, now, utc),
        )
    }

    @Test
    fun an_error_code_replaces_the_plain_state() {
        val lines = lastKnownLines(
            LastKnown(known = true, lastSeenAt = "2026-10-22T00:33:57Z", status = LastKnownStatus(state = 12, battery = 9, errorCode = 4)),
            now,
            utc,
        )

        assertEquals("Last heard from 12:33 AM", lines[0])
        assertEquals(true, lines[1].startsWith("Last reported: Battery 9%, "))
    }

    @Test
    fun a_robot_with_no_details_still_says_it_is_not_answering() {
        assertEquals(listOf("Not answering"), lastKnownLines(LastKnown(known = true), now, utc))
    }
}
