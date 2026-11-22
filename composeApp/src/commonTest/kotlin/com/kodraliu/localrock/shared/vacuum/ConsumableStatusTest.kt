package com.kodraliu.localrock.shared.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals

class ConsumableStatusTest {
    @Test
    fun brushPercentRoundsUpLikeTheOfficialApp() {
        assertEquals(48, ConsumableStatus.percentLeft(568_488L, ConsumableStatus.MAIN_BRUSH_LIFETIME_S))
        assertEquals(22, ConsumableStatus.percentLeft(568_488L, ConsumableStatus.SIDE_BRUSH_LIFETIME_S))
    }

    @Test
    fun overdueItemsStopAtZero() {
        assertEquals(0, ConsumableStatus.percentLeft(568_488L, ConsumableStatus.FILTER_LIFETIME_S))
        assertEquals(0, ConsumableStatus.percentLeft(568_469L, ConsumableStatus.SENSOR_LIFETIME_S))
    }

    @Test
    fun runCountersAreCountsNotSeconds() {
        assertEquals(100, ConsumableStatus.percentLeft(1L, ConsumableStatus.STRAINER_LIFETIME_RUNS))
        assertEquals(50, ConsumableStatus.percentLeft(75L, ConsumableStatus.STRAINER_LIFETIME_RUNS))
        assertEquals(0, ConsumableStatus.percentLeft(150L, ConsumableStatus.STRAINER_LIFETIME_RUNS))
    }
}
