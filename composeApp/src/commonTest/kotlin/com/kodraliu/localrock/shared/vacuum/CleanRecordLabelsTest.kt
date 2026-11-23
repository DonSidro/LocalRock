package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class CleanRecordLabelsTest {
    // The two runs a Qrevo Pro reported through get_clean_record.
    private val scheduled = CleanRecord(
        beginEpoch = 1793458813, endEpoch = 1793476533, durationSeconds = 9911, areaMm2 = 92_852_500,
        errorCode = 0, complete = 1, startType = 3, cleanType = 1, finishReason = 52,
        dustCollected = true, washCount = 17, cleanedAreaMm2 = 122_650_000,
    )
    private val interrupted = CleanRecord(
        beginEpoch = 1793490296, endEpoch = 1793493676, durationSeconds = 50, areaMm2 = 0,
        errorCode = 0, complete = 0, startType = 2, cleanType = 1, finishReason = 21,
        cleanedAreaMm2 = 0,
    )

    @Test
    fun scheduledRunReadsLikeTheOfficialApp() {
        assertEquals("Finished cleaning", finishReasonLabel(scheduled))
        assertEquals(listOf("Schedules", "Full", "Dust collected", "Mop wash x 17"), cleanRecordTags(scheduled))
        assertEquals("165 min", cleanDurationLabel(scheduled.durationSeconds))
        assertEquals("123 m²", cleanAreaLabel(scheduled.shownAreaMm2))
    }

    @Test
    fun interruptedRunReadsLikeTheOfficialApp() {
        assertEquals("Cleaning interrupted by user", finishReasonLabel(interrupted))
        assertEquals(listOf("App", "Full"), cleanRecordTags(interrupted))
        assertEquals("50 s", cleanDurationLabel(interrupted.durationSeconds))
        assertEquals("0.0 m²", cleanAreaLabel(interrupted.shownAreaMm2))
    }

    @Test
    fun areaFallsBackToAreaWhenCleanedAreaIsAbsent() {
        assertEquals(92_852_500, scheduled.copy(cleanedAreaMm2 = null).shownAreaMm2)
    }

    @Test
    fun unknownFinishReasonFollowsComplete() {
        assertEquals("Finished cleaning", finishReasonLabel(scheduled.copy(finishReason = 999)))
        assertEquals("Cleanup Interrupted", finishReasonLabel(interrupted.copy(finishReason = 999)))
    }

    @Test
    fun totalsAreCompact() {
        assertEquals("29.9k m²", totalAreaLabel(29_927_845_000))
        assertEquals("812 m²", totalAreaLabel(812_300_000))
    }
}
