package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.ceil

@Serializable
data class ConsumableStatus(
    @SerialName("main_brush_work_time") val mainBrushWorkTime: Long? = null,
    @SerialName("side_brush_work_time") val sideBrushWorkTime: Long? = null,
    @SerialName("filter_work_time") val filterWorkTime: Long? = null,
    @SerialName("sensor_dirty_time") val sensorDirtyTime: Long? = null,
    // Wash-dock consumables; only reported by dock-equipped models. Unlike the
    // others, the "_times" ones count runs since the last reset, not seconds.
    @SerialName("cleaning_brush_work_times") val cleaningBrushWorkTime: Long? = null,
    @SerialName("strainer_work_times") val strainerWorkTime: Long? = null,
) {
    companion object {
        const val MAIN_BRUSH_LIFETIME_S = 300L * 3600
        const val SIDE_BRUSH_LIFETIME_S = 200L * 3600
        const val FILTER_LIFETIME_S = 150L * 3600
        const val SENSOR_LIFETIME_S = 30L * 3600
        const val CLEANING_BRUSH_LIFETIME_RUNS = 300L
        const val STRAINER_LIFETIME_RUNS = 150L

        /** Share of the life left, rounded up and clamped to 0..100, as the official app shows it. */
        fun percentLeft(used: Long, total: Long): Int =
            ceil(100.0 * (1.0 - used.toDouble() / total)).toInt().coerceIn(0, 100)
    }
}
