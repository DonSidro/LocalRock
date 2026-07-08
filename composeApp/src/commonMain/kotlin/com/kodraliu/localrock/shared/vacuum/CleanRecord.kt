package com.kodraliu.localrock.shared.vacuum

data class CleanRecord(
    val beginEpoch: Long,
    val endEpoch: Long,
    val durationSeconds: Long,
    val areaMm2: Long,
    val errorCode: Int,
    val complete: Int,
)
