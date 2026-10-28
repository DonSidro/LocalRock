package com.kodraliu.localrock.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowLayoutTest {

    @Test
    fun two_pane_starts_at_600_dp_wide_and_500_dp_tall() {
        assertEquals(WindowLayout(false), windowLayoutFor(599.9f, 900f))
        assertEquals(WindowLayout(true), windowLayoutFor(600f, 900f))
        assertEquals(WindowLayout(false), windowLayoutFor(700f, 499.9f))
        assertEquals(WindowLayout(true), windowLayoutFor(700f, 500f))
    }

    @Test
    fun duo_form_factors_classify_by_window_size() {
        assertEquals(WindowLayout(false), windowLayoutFor(466f, 678f)) // Duo outer portrait
        assertEquals(WindowLayout(false), windowLayoutFor(678f, 466f)) // Duo outer landscape
        assertEquals(WindowLayout(true), windowLayoutFor(669f, 951f)) // Duo inner portrait
        assertEquals(WindowLayout(true), windowLayoutFor(951f, 669f)) // Duo inner landscape
    }

    @Test
    fun iphone_ipad_and_split_view_classify_by_window_size() {
        assertEquals(WindowLayout(false), windowLayoutFor(932f, 430f)) // iPhone landscape
        assertEquals(WindowLayout(true), windowLayoutFor(834f, 1194f)) // iPad 11" portrait
        assertEquals(WindowLayout(false), windowLayoutFor(320f, 1024f)) // iPad Split View narrow pane
    }
}
