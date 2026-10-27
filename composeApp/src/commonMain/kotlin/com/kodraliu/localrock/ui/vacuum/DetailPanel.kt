package com.kodraliu.localrock.ui.vacuum

/** The sheet (and, on wide windows, side pane) currently shown on the robot detail screen. */
internal enum class DetailPanel { More, Routines, Rooms, Order, Dock, CleaningMode, Maps }

internal fun DetailPanel.title(): String = when (this) {
    DetailPanel.More -> "More actions"
    DetailPanel.Routines -> "Routines"
    DetailPanel.Rooms -> "Rooms"
    DetailPanel.Order -> "Clean order"
    DetailPanel.Dock -> "Dock & care"
    DetailPanel.CleaningMode -> "Cleaning mode"
    DetailPanel.Maps -> "Maps"
}

/** True when a panel is shown as a headed view in the wide-window side pane. `More` is the pane's home content, not a headed panel. */
internal fun sidePaneShowsPanel(panel: DetailPanel?): Boolean = panel != null && panel != DetailPanel.More

/** Actions offered by the "More" panel; sheet actions open a panel, the rest navigate away. */
internal enum class MoreAction { ROOMS, ZONES, PIN_GO, REMOTE, SCHEDULE, CAMERA, DOCK, HISTORY, MAPS, EDIT_MAP, ROUTINES }

/** The panel a More action opens, or null when it navigates away instead. */
internal fun panelForMoreAction(action: MoreAction): DetailPanel? = when (action) {
    MoreAction.ROOMS -> DetailPanel.Rooms
    MoreAction.DOCK -> DetailPanel.Dock
    MoreAction.MAPS -> DetailPanel.Maps
    MoreAction.ROUTINES -> DetailPanel.Routines
    else -> null
}
