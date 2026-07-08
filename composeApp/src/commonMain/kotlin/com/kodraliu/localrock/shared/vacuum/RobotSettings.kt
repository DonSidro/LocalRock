package com.kodraliu.localrock.shared.vacuum

data class RobotSettings(
    val volume: Int = 50,
    val childLock: Boolean = false,
    val ledEnabled: Boolean = true,
    val carpetMode: Int = 1, // 0=avoid, 1=standard, 2=boost
)

data class DockSettings(
    val washMode: Int = 1,      // 0=light, 1=standard, 2=intensive
    val washFreq: Int = 0,      // 0=every time, 1=every 2, 2=every 3
    val autoEmptyMode: Int = 0, // 0=smart, 1=max, 2=normal
)

data class CleanSummary(
    val totalTimeSec: Long,
    val totalAreaMm2: Long,
    val totalCount: Int,
)

data class DndSettings(
    val enabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
)

data class FloorMap(val name: String, val mapFlag: Int)
