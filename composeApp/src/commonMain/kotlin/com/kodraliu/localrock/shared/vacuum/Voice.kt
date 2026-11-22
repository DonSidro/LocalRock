package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** The `newFeatureSet` bit for voice control robots, which the official app gives a Voice Assistant page. */
const val VOICE_ASSISTANT_BIT = 37

fun isVoiceAssistantSupported(newFeatureSet: String?): Boolean = newFeatureBit(newFeatureSet, VOICE_ASSISTANT_BIT)

suspend fun VacuumSession.getVoiceServiceSwitch(): V1Response = sendCommand("get_voice_service_switch")
suspend fun VacuumSession.setVoiceServiceSwitch(params: JsonElement): V1Response = sendCommandRaw("set_voice_service_switch", params)
suspend fun VacuumSession.getCurrentSound(): V1Response = sendCommand("get_current_sound")
suspend fun VacuumSession.getVoiceHistorySummary(): V1Response = sendCommand("get_voice_history_summary")
suspend fun VacuumSession.getVoiceHistory(minId: Int, maxId: Int): V1Response =
    sendCommandRaw("get_voice_history", JsonObject(mapOf("min_id" to JsonPrimitive(minId), "max_id" to JsonPrimitive(maxId))))

/** The Voice Assistant switch as `get_voice_service_switch` reports it, e.g. `{"data":1}`. */
data class VoiceSwitch(val key: String?, val enabled: Boolean)

fun parseVoiceSwitch(result: JsonElement?): VoiceSwitch? {
    val (key, value) = parseBetaReply(result) ?: return null
    return if (value == 0 || value == 1) VoiceSwitch(key, value == 1) else null
}

/** `set_voice_service_switch` takes only the bare object, `{"data":1}`; other shapes time out instead of being refused. */
fun voiceSwitchParams(key: String, on: Boolean): JsonElement = JsonObject(mapOf(key to JsonPrimitive(if (on) 1 else 0)))

/** `get_voice_history_summary`: the id range the robot holds and how many messages. */
data class VoiceHistorySummary(val minId: Int, val maxId: Int, val count: Int)

/** One chat line; the official chat page draws speaker 1 as the user and every other value as the robot. */
data class VoiceMessage(val id: Int, val ts: Long, val text: String, val speaker: Int, val offline: Int?) {
    val fromUser: Boolean get() = speaker == 1
}

/** What the Voice Assistant page shows besides the switch and volume; null means the read failed. */
data class VoiceSettings(
    val languageCode: String? = null,
    val summary: VoiceHistorySummary? = null,
    val history: List<VoiceMessage> = emptyList(),
)

/** The official chat page asks for ids in windows of 100 (min_id to min_id + 100). */
const val VOICE_HISTORY_WINDOW = 100

private fun JsonElement?.firstObject(): JsonObject? =
    (if (this is JsonArray) firstOrNull() else this) as? JsonObject

private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

fun parseVoiceHistorySummary(result: JsonElement?): VoiceHistorySummary? {
    val obj = result.firstObject() ?: return null
    val count = obj.int("count") ?: return null
    return VoiceHistorySummary(obj.int("min_id") ?: 0, obj.int("max_id") ?: 0, count)
}

fun parseVoiceHistory(result: JsonElement?): List<VoiceMessage>? {
    val history = result.firstObject()?.get("history") as? JsonArray ?: return null
    return history.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val id = o.int("id") ?: return@mapNotNull null
        VoiceMessage(
            id = id,
            ts = (o["ts"] as? JsonPrimitive)?.longOrNull ?: 0L,
            text = (o["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            speaker = o.int("speaker") ?: 0,
            offline = o.int("offline"),
        )
    }
}

/** The (min_id, max_id) pairs to read every message in [summary], oldest first. */
fun voiceHistoryWindows(summary: VoiceHistorySummary): List<Pair<Int, Int>> {
    if (summary.count <= 0 || summary.maxId < summary.minId) return emptyList()
    return (summary.minId..summary.maxId step VOICE_HISTORY_WINDOW).map { it to it + VOICE_HISTORY_WINDOW }
}

const val VOICE_HISTORY_RETRY_MS = 500L

/** Reads the newest windows first and retries a failed one once, so a dropped reply costs older lines, not the newest. */
suspend fun readVoiceHistory(
    windows: List<Pair<Int, Int>>,
    maxWindows: Int = 10,
    fetch: suspend (Int, Int) -> List<VoiceMessage>?,
): List<VoiceMessage>? {
    val out = mutableListOf<VoiceMessage>()
    var failed = false
    for ((min, max) in windows.takeLast(maxWindows).asReversed()) {
        val page = fetch(min, max) ?: run { delay(VOICE_HISTORY_RETRY_MS); fetch(min, max) }
        if (page == null) { failed = true; break }
        out += page
    }
    if (failed && out.isEmpty()) return null
    return out.distinctBy { it.id }.sortedBy { it.id }
}

/** The `language` code from `get_current_sound`, e.g. `[{"sid_in_use":3,...,"language":"en"}]`. */
fun parseCurrentSoundLanguage(result: JsonElement?): String? =
    (result.firstObject()?.get("language") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

// Names as the official Language page titles its voice packs; codes the app folds into one (en-uk, es-la) share it.
private val voiceLanguageNames = mapOf(
    "prc" to "标准普通话版", // verified
    "tw" to "臺灣普通話版", // verified
    "en" to "English", // verified
    "it" to "Italiano", // verified
    "ja" to "日本語", // verified
    "ko" to "한국어 버전", // verified
    "de" to "Deutsch", // verified
    "es" to "Español", // verified
    "fr" to "Français", // verified
    "uk" to "Українська", // verified
    "he" to "עברית", // verified
    "iw" to "עברית", // verified
    "pl" to "Polski", // verified
    "ro" to "Română", // verified
    "ru" to "Русский", // inferred
    "tr" to "Türkçe", // inferred
    "th" to "ไทย", // inferred
    "pt" to "Português", // inferred
    "nl" to "Nederlands", // inferred
)

/** The display name for a voice language code, or the code itself when it is not known. */
fun voiceLanguageName(code: String): String {
    val key = code.lowercase()
    return voiceLanguageNames[key]
        ?: voiceLanguageNames[key.substringBefore('-').substringBefore('_')]
        ?: code
}

/** Whether each message gets a timestamp: user lines only, the first one and any more than 60 s after the last shown. */
fun voiceTimestampsShown(messages: List<VoiceMessage>): List<Boolean> {
    var lastShown = -1L
    return messages.mapIndexed { i, m ->
        val due = i == 0 || m.ts - lastShown > 60
        if (due) lastShown = m.ts
        m.fromUser && due
    }
}

/** The official chat stamp, `2026/11/12 09:03`: month and day unpadded, hours and minutes padded. */
fun formatVoiceTimestamp(ts: Long, utcOffsetSeconds: Int): String {
    val local = ts + utcOffsetSeconds
    val days = local.floorDiv(86_400L)
    val secondOfDay = local.mod(86_400L)
    // Civil date from days since 1970-01-01 (Howard Hinnant's algorithm).
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun pad(v: Long) = v.toString().padStart(2, '0')
    return "$year/$month/$day ${pad(secondOfDay / 3_600)}:${pad(secondOfDay % 3_600 / 60)}"
}
