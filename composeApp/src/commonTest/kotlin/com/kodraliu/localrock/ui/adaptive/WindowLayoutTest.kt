package com.kodraliu.localrock.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowLayoutTest {

    @Test
    fun width_class_breaks_at_600_and_840() {
        assertEquals(WidthClass.Compact, windowLayoutFor(599f, 800f).widthClass)
        assertEquals(WidthClass.Medium, windowLayoutFor(600f, 800f).widthClass)
        assertEquals(WidthClass.Medium, windowLayoutFor(839f, 800f).widthClass)
        assertEquals(WidthClass.Expanded, windowLayoutFor(840f, 800f).widthClass)
    }

    @Test
    fun two_pane_needs_both_600_width_and_500_height() {
        assertFalse(windowLayoutFor(599f, 800f).twoPane)
        assertFalse(windowLayoutFor(600f, 499f).twoPane)
        assertFalse(windowLayoutFor(840f, 499f).twoPane)
        assertTrue(windowLayoutFor(600f, 500f).twoPane)
        assertTrue(windowLayoutFor(840f, 800f).twoPane)
    }
}
