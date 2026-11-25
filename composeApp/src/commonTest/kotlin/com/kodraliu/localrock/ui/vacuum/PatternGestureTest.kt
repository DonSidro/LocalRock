package com.kodraliu.localrock.ui.vacuum

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals

class PatternGestureTest {
    private val centers = List(9) { Offset((it % 3 + .5f) * 100, (it / 3 + .5f) * 100) }

    @Test fun fast_swipe_collects_crossed_dots_in_both_directions() {
        assertEquals(listOf(0, 1, 2), patternSegmentHits(centers, centers[0], centers[2], 34f))
        assertEquals(listOf(2, 1, 0), patternSegmentHits(centers, centers[2], centers[0], 34f))
        assertEquals(listOf(0, 4, 8), patternSegmentHits(centers, centers[0], centers[8], 34f))
    }

    @Test fun release_segment_can_supply_the_final_dot_and_preserves_digit_encoding() {
        val selected = mutableListOf<Int>()
        val points = listOf(centers[0], centers[2], centers[5])
        patternSegmentHits(centers, points[0], points[0], 34f).forEach { appendPatternDot(selected, it) }
        points.zipWithNext { a, b -> patternSegmentHits(centers, a, b, 34f).forEach { appendPatternDot(selected, it) } }
        assertEquals("1236", patternDigits(selected))
    }

    @Test fun incomplete_patterns_clear_the_credential_and_dots_do_not_repeat() {
        assertEquals("", patternDigits(emptyList()))
        assertEquals("", patternDigits(listOf(0, 1, 2)))
        val selected = mutableListOf(0)
        appendPatternDot(selected, 2)
        appendPatternDot(selected, 0)
        appendPatternDot(selected, 8)
        assertEquals("12369", patternDigits(selected))
    }

    @Test fun moves_outside_hit_regions_do_not_add_dots() {
        assertEquals(emptyList(), patternSegmentHits(centers, Offset.Zero, Offset(300f, 0f), 34f))
    }
}
