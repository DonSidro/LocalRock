package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// get_clean_detail {begin, type:0, id:0, from}, read on a Qrevo Curv 2 Flow. The Qrevo Pro answers "not recognized".

/** One raw event: [time] in epoch seconds, [type] the event id (`i`), [fields] the payload keyed by field id. */
data class CleanTimelineEvent(val time: Long, val type: Int, val fields: Map<Int, JsonElement>) {
    fun int(key: Int): Int? = (fields[key] as? JsonPrimitive)?.intOrNull
}

/** One page of `get_clean_detail`; ask for the next page at `from + num` while [more] is true. */
data class CleanTimelinePage(val from: Int, val num: Int, val more: Boolean, val events: List<CleanTimelineEvent>) {
    val nextFrom: Int get() = from + num
}

data class CleanStartSettings(val suction: Int, val water: Int, val route: Int, val repeat: Int)

sealed interface CleanTimelineStep {
    val time: Long

    /** [scope]: 1 whole home, 2 drawn zone, 3 selected rooms. */
    data class Start(override val time: Long, val scope: Int, val settings: CleanStartSettings?) : CleanTimelineStep
    data class Room(override val time: Long, val segmentId: Int, val roomTypeTag: Int?) : CleanTimelineStep
    data class WashMop(override val time: Long, val mode: Int) : CleanTimelineStep
    data class AutoEmptying(override val time: Long, val mode: Int) : CleanTimelineStep
    data class MopLiftedForCarpet(override val time: Long) : CleanTimelineStep
    data class Pause(override val time: Long) : CleanTimelineStep
    data class Resume(override val time: Long) : CleanTimelineStep

    /** [code] is 0 when no error event came before the switch to the error state. */
    data class Error(override val time: Long, val code: Int, val subCode: Int) : CleanTimelineStep
    data class ExtraClean(override val time: Long, val reason: Int) : CleanTimelineStep
    data class Finished(override val time: Long) : CleanTimelineStep
}

object CleanTimelineParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null when [result] is not the `get_clean_detail` envelope (an empty `{}` is an empty page). */
    fun parsePage(result: JsonElement?): CleanTimelinePage? {
        val obj = (result as? JsonObject) ?: return null
        val events = (obj["events"] as? JsonArray)?.mapNotNull(::parseEvent).orEmpty()
        return CleanTimelinePage(
            from = obj["from"].asInt() ?: 0,
            num = obj["num"].asInt() ?: events.size,
            more = obj["more"].asInt() == 1,
            events = events,
        )
    }

    private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

    private fun parseEvent(element: JsonElement): CleanTimelineEvent? {
        val obj = element as? JsonObject ?: return null
        val time = (obj["t"] as? JsonPrimitive)?.longOrNull ?: return null
        val type = obj["i"].asInt() ?: return null
        val payload = when (val e = obj["e"]) {
            is JsonObject -> e
            // The robot sends the payload as a JSON string; the official app strips backslashes first.
            is JsonPrimitive -> runCatching { json.parseToJsonElement(e.content.replace("\\", "")).jsonObject }.getOrNull()
            else -> null
        } ?: return null
        val fields = payload.entries.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap()
        return CleanTimelineEvent(time, type, fields)
    }
}

object CleanTimeline {
    // Official app: a pause node only shows when the robot was in one of these states.
    private val PAUSABLE_STATES = setOf(5, 17, 18, 6, 15, 22, 25, 26, 33, 34, 35, 38, 39)
    private val RESUME_REASONS = setOf(1, 2, 7, 16)
    private const val STATE_ERROR = 12
    private const val STATE_PAUSED = 10

    /** Builds the steps the official app shows from the raw [events]; [endEpoch] adds the finish node when positive. */
    fun build(events: List<CleanTimelineEvent>, endEpoch: Long = 0L): List<CleanTimelineStep> {
        // Same order as the official app: by time, state switches (11) last among equal times.
        val sorted = events.sortedWith(compareBy<CleanTimelineEvent> { it.time }.thenBy { if (it.type == 11) 1 else 0 })
        val steps = mutableListOf<CleanTimelineStep>()
        var lastError: Pair<Int, Int>? = null
        for (e in sorted) {
            when (e.type) {
                25 -> e.int(252)?.let { scope ->
                    val settings = if (e.int(3) != null) {
                        CleanStartSettings(e.int(3) ?: 0, e.int(4) ?: 0, e.int(5) ?: 0, e.int(6) ?: 1)
                    } else null
                    steps += CleanTimelineStep.Start(e.time, scope, settings)
                }
                // 42 with a clean preference (421) opens a room; ids from 35 up are extra passes shown through event 61.
                42 -> e.int(2)?.takeIf { it in 1..30 && e.int(421) != null }?.let {
                    steps += CleanTimelineStep.Room(e.time, it, e.int(422))
                }
                52 -> e.int(521)?.let { steps += CleanTimelineStep.WashMop(e.time, it) }
                54 -> steps += CleanTimelineStep.AutoEmptying(e.time, e.int(541) ?: -1)
                45 -> if (e.int(451) == 1) steps += CleanTimelineStep.MopLiftedForCarpet(e.time)
                61 -> steps += CleanTimelineStep.ExtraClean(e.time, e.int(611) ?: 0)
                31 -> lastError = (e.int(311) ?: 0) to (e.int(312) ?: 0)
                13 -> if (e.int(131) in RESUME_REASONS) steps += CleanTimelineStep.Resume(e.time)
                11 -> when (e.int(112)) {
                    STATE_ERROR -> steps += CleanTimelineStep.Error(e.time, lastError?.first ?: 0, lastError?.second ?: 0)
                    STATE_PAUSED -> if (e.int(111) in PAUSABLE_STATES) steps += CleanTimelineStep.Pause(e.time)
                }
            }
        }
        if (endEpoch > 0L) steps += CleanTimelineStep.Finished(endEpoch)
        return steps
    }
}

/** English labels, from the official app's string table. */
object CleanTimelineLabels {
    /** "5-1" for error 5, sub-error 1; just "5" when there is no sub-error. */
    fun errorCodeText(code: Int, subCode: Int): String = if (subCode > 0) "$code-$subCode" else "$code"

    fun title(step: CleanTimelineStep, roomName: (Int) -> String? = { null }): String = when (step) {
        is CleanTimelineStep.Start -> when (step.scope) {
            2 -> "Start zone cleaning"
            3 -> "Start selective room cleaning"
            else -> "Start full cleaning"
        }
        is CleanTimelineStep.Room -> "Clean " + roomLabel(step, roomName)
        is CleanTimelineStep.WashMop -> when (step.mode) {
            12 -> "On-board dirty water tank draining"
            13 -> "Autonomous mop washing at the dock"
            14 -> "Robot water tank refilling"
            else -> "Wash Mop"
        }
        is CleanTimelineStep.AutoEmptying -> "Auto Emptying"
        is CleanTimelineStep.MopLiftedForCarpet -> "Lift up the mop cloth to clean carpet"
        is CleanTimelineStep.Pause -> "Pause"
        is CleanTimelineStep.Resume -> "Resume"
        is CleanTimelineStep.Error ->
            if (step.code > 0) "An error occurs. (${errorCodeText(step.code, step.subCode)})" else "An error occurs."
        is CleanTimelineStep.ExtraClean -> when (step.reason) {
            1, 2 -> "Add Area"
            3 -> "Automatic Re-Mopping"
            4 -> "Heavy Dirt/Stain Recognition"
            6 -> "Clean Areas Blocked by People/Pets"
            else -> "Extra cleaning"
        }
        is CleanTimelineStep.Finished -> "Cleaning Finished"
    }

    /** A second line for the steps that carry settings or a cause, or null. */
    fun detail(step: CleanTimelineStep): String? = when (step) {
        is CleanTimelineStep.Start -> step.settings?.let(::settingsText)
        is CleanTimelineStep.AutoEmptying -> AUTO_EMPTY_MODES.getOrNull(step.mode)
        is CleanTimelineStep.Error -> VacuumErrorCodes.describe(step.code).takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun roomLabel(step: CleanTimelineStep.Room, roomName: (Int) -> String?): String =
        roomName(step.segmentId)?.takeIf { it.isNotBlank() }
            ?: step.roomTypeTag?.let { RoomType.fromTag(it)?.label }
            ?: "Room ${step.segmentId}"

    private val AUTO_EMPTY_MODES = listOf("Smart", "Light", "Balanced", "Turbo", "Max")

    private val SUCTION = mapOf(
        1 to "Quiet", 2 to "Standard", 3 to "Strong", 4 to "MAX", 5 to "Gentle", 6 to "Custom",
        7 to "Carpet", 8 to "MAX+", 9 to "Main brush lifted", 10 to "Easy",
    )
    private val ROUTE = mapOf(0 to "Standard", 1 to "Deep", 2 to "Custom", 3 to "Deep+", 4 to "Fast")
    private val WATER = mapOf(0 to "Off", 1 to "Low", 2 to "Medium", 3 to "High", 4 to "Custom")

    private fun settingsText(s: CleanStartSettings): String {
        // Water 21..50 is the 30-step flow (codes 221-250).
        val water = WATER[s.water] ?: if (s.water in 21..50) "Level ${s.water - 20}" else "Water ${s.water}"
        val parts = mutableListOf(
            "Suction ${SUCTION[s.suction] ?: s.suction}",
            "Water $water",
            "Route ${ROUTE[s.route] ?: s.route}",
        )
        if (s.repeat > 1) parts += "${s.repeat} passes"
        return parts.joinToString(" · ")
    }
}
