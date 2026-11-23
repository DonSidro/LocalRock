package com.kodraliu.localrock.ui.vacuum.detail

import com.kodraliu.localrock.ui.strings.DeviceStrings

/** The sheet (and, on wide windows, side pane) currently shown on the robot detail screen. */
internal enum class DetailPanel { Order, Dock, CleaningMode, Maps }

internal fun DetailPanel.title(): String = when (this) {
    DetailPanel.Order -> DeviceStrings.DEFAULT_ORDER
    DetailPanel.Dock -> DeviceStrings.DOCK_CONTROL
    DetailPanel.CleaningMode -> DeviceStrings.CLEANING_MODE
    DetailPanel.Maps -> DeviceStrings.MAPS
}

/** True when a panel is shown as a headed view in the wide-window side pane. */
internal fun sidePaneShowsPanel(panel: DetailPanel?): Boolean = panel != null
