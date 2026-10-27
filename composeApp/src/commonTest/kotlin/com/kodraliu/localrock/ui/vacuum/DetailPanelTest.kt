package com.kodraliu.localrock.ui.vacuum

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailPanelTest {

    @Test
    fun panelForMoreActionMapsSheetActions() {
        assertEquals(DetailPanel.Rooms, panelForMoreAction(MoreAction.ROOMS))
        assertEquals(DetailPanel.Dock, panelForMoreAction(MoreAction.DOCK))
        assertEquals(DetailPanel.Maps, panelForMoreAction(MoreAction.MAPS))
        assertEquals(DetailPanel.Routines, panelForMoreAction(MoreAction.ROUTINES))
    }

    @Test
    fun panelForMoreActionIsNullForNavigationActions() {
        assertNull(panelForMoreAction(MoreAction.ZONES))
        assertNull(panelForMoreAction(MoreAction.PIN_GO))
        assertNull(panelForMoreAction(MoreAction.REMOTE))
        assertNull(panelForMoreAction(MoreAction.SCHEDULE))
        assertNull(panelForMoreAction(MoreAction.CAMERA))
        assertNull(panelForMoreAction(MoreAction.HISTORY))
        assertNull(panelForMoreAction(MoreAction.EDIT_MAP))
    }

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
