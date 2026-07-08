package com.kodraliu.localrock.shared.vacuum

data class CleanTimer(
    val id: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val weekdays: Set<Int>, // 0=Sun, 1=Mon … 6=Sat
)
