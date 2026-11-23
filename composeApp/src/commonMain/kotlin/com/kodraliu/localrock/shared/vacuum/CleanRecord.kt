package com.kodraliu.localrock.shared.vacuum

data class CleanRecord(
    val beginEpoch: Long,
    val endEpoch: Long,
    val durationSeconds: Long,
    val areaMm2: Long,
    val errorCode: Int,
    val complete: Int,
    val startType: Int = 0,
    val cleanType: Int = 0,
    val finishReason: Int = 0,
    val dustCollected: Boolean = false,
    val washCount: Int = 0,
    val cleanedAreaMm2: Long? = null,
) {
    /** The area the official app shows for a run: `cleaned_area` when the robot reports it, else `area`. */
    val shownAreaMm2: Long get() = cleanedAreaMm2 ?: areaMm2
}
