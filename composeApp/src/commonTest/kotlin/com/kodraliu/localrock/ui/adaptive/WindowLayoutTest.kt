package com.kodraliu.localrock.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowLayoutTest {

    @Test
    fun width_class_breaks_at_600_and_840_dp() {
        assertEquals(WindowLayout(WidthClass.Compact, false), windowLayoutFor(599.9f, 900f))
        assertEquals(WindowLayout(WidthClass.Medium, true), windowLayoutFor(600f, 900f))
        assertEquals(WindowLayout(WidthClass.Medium, true), windowLayoutFor(839.9f, 900f))
        assertEquals(WindowLayout(WidthClass.Expanded, true), windowLayoutFor(840f, 900f))
    }

    @Test
    fun two_pane_requires_height_of_at_least_500_dp() {
        assertEquals(WindowLayout(WidthClass.Medium, false), windowLayoutFor(700f, 499.9f))
        assertEquals(WindowLayout(WidthClass.Medium, true), windowLayoutFor(700f, 500f))
    }

    @Test
    fun duo_form_factors_classify_by_window_size() {
        assertEquals(WindowLayout(WidthClass.Compact, false), windowLayoutFor(466f, 678f)) // Duo outer portrait
        assertEquals(WindowLayout(WidthClass.Medium, false), windowLayoutFor(678f, 466f)) // Duo outer landscape
        assertEquals(WindowLayout(WidthClass.Medium, true), windowLayoutFor(669f, 951f)) // Duo inner portrait
        assertEquals(WindowLayout(WidthClass.Expanded, true), windowLayoutFor(951f, 669f)) // Duo inner landscape
    }

    @Test
    fun iphone_ipad_and_split_view_classify_by_window_size() {
        assertEquals(WindowLayout(WidthClass.Expanded, false), windowLayoutFor(932f, 430f)) // iPhone landscape
        // Brief lists this as Expanded, but 834 < 840, so the binding breakpoint gives Medium.
        assertEquals(WindowLayout(WidthClass.Medium, true), windowLayoutFor(834f, 1194f)) // iPad 11" portrait
        assertEquals(WindowLayout(WidthClass.Compact, false), windowLayoutFor(320f, 1024f)) // iPad Split View narrow pane
    }
}
