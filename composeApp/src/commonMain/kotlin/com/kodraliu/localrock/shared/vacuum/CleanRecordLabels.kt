package com.kodraliu.localrock.shared.vacuum

import kotlin.math.roundToInt

private val StartTypes = mapOf(
    1 to "Button",
    2 to "App",
    3 to "Schedules",
    4 to "Mi home",
    5 to "Quick start",
    13 to "Voice Control",
    19 to "Hatch",
    101 to "Routines",
    121 to "Matter",
    801 to "Alexa",
    802 to "Google",
    803 to "IFTTT",
)

private const val INTERRUPTED_BY_USER = "Cleaning interrupted by user"
private const val CLEANUP_INTERRUPTED = "Cleanup Interrupted"
private const val FINISHED = "Finished cleaning"
private const val COULD_NOT_CONTINUE = "Could not continue cleaning"

/** The official app's table from `finish_reason` to the result line; reasons it has no text for are absent. */
private val FinishReasons: Map<Int, String> = buildMap {
    listOf(21, 29, 35, 36, 37, 43, 48, 49, 50, 60, 103).forEach { put(it, INTERRUPTED_BY_USER) }
    listOf(52, 54, 55, 56, 57).forEach { put(it, FINISHED) }
    listOf(24, 34, 51, 64, 101, 104, 105, 106, 107, 109, 110).forEach { put(it, CLEANUP_INTERRUPTED) }
    listOf(32, 33, 102).forEach { put(it, COULD_NOT_CONTINUE) }
    listOf(45, 65).forEach { put(it, "Positioning Failed") }
    listOf(61, 62).forEach { put(it, "Area unreachable") }
    put(67, "Washing Error")
    put(68, "Failed to return to the dock")
}

/** What started the run, or null for a start type the official app has no name for. */
fun startTypeLabel(startType: Int): String? = StartTypes[startType]

/** Only the full clean (1) is named; the other `clean_type` values are not mapped. */
fun cleanTypeLabel(cleanType: Int): String? = if (cleanType == 1) "Full" else null

/** The result line for a run, falling back to what `complete` says when the reason has no text. */
fun finishReasonLabel(record: CleanRecord): String =
    FinishReasons[record.finishReason] ?: if (record.complete == 1) FINISHED else CLEANUP_INTERRUPTED

/** The tags under the result line, in the official app's order: start, mode, dust, mop washes. */
fun cleanRecordTags(record: CleanRecord): List<String> = buildList {
    startTypeLabel(record.startType)?.let { add(it) }
    cleanTypeLabel(record.cleanType)?.let { add(it) }
    if (record.dustCollected) add("Dust collected")
    if (record.washCount > 0) add("Mop wash x ${record.washCount}")
}

/** Seconds under a minute ("50 s"), minutes rounded to the nearest otherwise ("165 min"). */
fun cleanDurationLabel(seconds: Long): String =
    if (seconds < 60) "$seconds s" else "${(seconds / 60.0).roundToInt()} min"

/** A run's area: one decimal under 1 m² ("0.0 m²"), whole square metres otherwise ("123 m²"). */
fun cleanAreaLabel(areaMm2: Long): String {
    val m2 = areaMm2 / 1_000_000.0
    return if (m2 < 1.0) "${(m2 * 10).roundToInt() / 10}.${(m2 * 10).roundToInt() % 10} m²" else "${m2.roundToInt()} m²"
}

/** The lifetime total area: "29.9k m²" from 1000 m² up. */
fun totalAreaLabel(areaMm2: Long): String {
    val m2 = areaMm2 / 1_000_000.0
    if (m2 < 1000.0) return "${m2.roundToInt()} m²"
    val tenths = (m2 / 100.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}k m²"
}
