package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConsumableStatus(
    @SerialName("main_brush_work_time") val mainBrushWorkTime: Long? = null,
    @SerialName("side_brush_work_time") val sideBrushWorkTime: Long? = null,
    @SerialName("filter_work_time") val filterWorkTime: Long? = null,
    @SerialName("sensor_dirty_time") val sensorDirtyTime: Long? = null,
    // Wash-dock consumables; only reported by dock-equipped models. Despite the
    // "_times" wire names these are used seconds, same as the others.
    @SerialName("cleaning_brush_work_times") val cleaningBrushWorkTime: Long? = null,
    @SerialName("strainer_work_times") val strainerWorkTime: Long? = null,
) {
    companion object {
        const val MAIN_BRUSH_LIFETIME_S = 300L * 3600
        const val SIDE_BRUSH_LIFETIME_S = 200L * 3600
        const val FILTER_LIFETIME_S = 150L * 3600
        const val SENSOR_LIFETIME_S = 30L * 3600
        const val CLEANING_BRUSH_LIFETIME_S = 300L * 3600
        const val STRAINER_LIFETIME_S = 150L * 3600
    }
}
