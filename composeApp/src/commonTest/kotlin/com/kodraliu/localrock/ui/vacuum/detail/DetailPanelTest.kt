package com.kodraliu.localrock.ui.vacuum.detail

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DetailPanelTest {

    @Test
    fun everyPanelHasANonBlankTitle() {
        DetailPanel.entries.forEach { panel ->
            assertTrue(panel.title().isNotBlank(), "empty title for $panel")
        }
    }

    @Test
    fun sidePaneShowsEveryOpenPanel() {
        assertFalse(sidePaneShowsPanel(null))
        DetailPanel.entries.forEach { panel ->
            assertTrue(sidePaneShowsPanel(panel), "side pane should show $panel")
        }
    }
}
