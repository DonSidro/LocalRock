package com.kodraliu.localrock.ui.vacuum

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
    fun sidePaneShowsPanelOnlyForSheetPanels() {
        assertFalse(sidePaneShowsPanel(null))
        assertFalse(sidePaneShowsPanel(DetailPanel.More))
        DetailPanel.entries.filter { it != DetailPanel.More }.forEach { panel ->
            assertTrue(sidePaneShowsPanel(panel), "side pane should show $panel")
        }
    }
}
