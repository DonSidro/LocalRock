package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

suspend fun VacuumSession.getSoundProgress(): V1Response = sendCommand("get_sound_progress")

// The robot downloads the pack from params.url itself, so the URL must be reachable from the robot.
suspend fun VacuumSession.installSound(pack: VoicePack): V1Response = sendCommandRaw("dnld_install_sound", installSoundParams(pack))

/** One entry of the plugin's voice-pkg `info` list (`voice_list`), as the server passes it through. */
data class VoicePack(
    val id: Int,
    val version: Int,
    val lang: String?,
    val title: String,
    val subTitle: String?,
    val url: String,
    val md5: String,
    val priority: Int,
)

/** `get_current_sound`: the pack in use, its version and the one being installed (0 when none). */
data class CurrentSound(val sidInUse: Int, val sidVersion: Int, val sidInProgress: Int, val location: String?, val language: String?)

/** `get_sound_progress`: state 1 downloading, 2 installing, 3 done; other states with a sid set are failures. */
data class SoundProgress(val sidInProgress: Int, val progress: Int, val state: Int, val error: Int)

private fun JsonElement?.firstObj(): JsonObject? = (if (this is JsonArray) firstOrNull() else this) as? JsonObject

private fun JsonObject.intAt(key: String): Int? = (get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

private fun JsonObject.textAt(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun parseCurrentSound(result: JsonElement?): CurrentSound? {
    val o = result.firstObj() ?: return null
    val sid = o.intAt("sid_in_use") ?: return null
    return CurrentSound(sid, o.intAt("sid_version") ?: 0, o.intAt("sid_in_progress") ?: 0, o.textAt("location"), o.textAt("language"))
}

fun parseSoundProgress(result: JsonElement?): SoundProgress? {
    val o = result.firstObj() ?: return null
    val state = o.intAt("state") ?: return null
    return SoundProgress(o.intAt("sid_in_progress") ?: 0, o.intAt("progress") ?: 0, state, o.intAt("error") ?: 0)
}

/**
 * The packs the plugin's SoundPackageManager would list: `voice_list` entries whose `applicable`
 * names [location] (cn is called prc there), sorted by `voice_pri`. Null when the answer has no list.
 */
fun parseVoicePackList(info: JsonElement?, location: String?): List<VoicePack>? {
    val list = (info as? JsonObject)?.get("voice_list") as? JsonArray ?: return null
    val raw = location?.takeIf { it.isNotBlank() } ?: "cn"
    val loc = if (raw.equals("cn", ignoreCase = true)) "prc" else raw
    return list.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val applicable = o["applicable"] as? JsonArray
        if (applicable != null && applicable.none { (it as? JsonPrimitive)?.contentOrNull == loc }) return@mapNotNull null
        VoicePack(
            id = o.intAt("voice_id") ?: return@mapNotNull null,
            version = o.intAt("version") ?: 0,
            lang = o.textAt("lang"),
            title = o.textAt("voice_title") ?: return@mapNotNull null,
            subTitle = o.textAt("voice_sub_title"),
            url = o.textAt("voice_pkg_url") ?: return@mapNotNull null,
            md5 = o.textAt("voice_pkg_md5") ?: return@mapNotNull null,
            priority = o.intAt("voice_pri") ?: Int.MAX_VALUE,
        )
    }.sortedBy { it.priority }
}

/** Where one install stands after a progress read and, when the robot is idle, a current-sound read. */
sealed interface VoiceInstallStep {
    data class Working(val percent: Int?) : VoiceInstallStep
    data object Done : VoiceInstallStep
    data class Failed(val installFailed: Boolean) : VoiceInstallStep
}

/** Consecutive idle polls (nothing in progress, target not in use) after which an install counts as failed. */
const val VOICE_INSTALL_IDLE_POLLS = 5

/** The idle-poll count after one more poll: grows on an idle read of the robot, resets on anything else. */
fun nextVoiceIdlePolls(step: VoiceInstallStep, current: CurrentSound?, idlePolls: Int): Int =
    if (step is VoiceInstallStep.Working && step.percent == null && current != null) idlePolls + 1 else 0

/** Mirrors the plugin's getProgess: busy on state 1 or 2, failure on any other state while a sid is set. */
fun voiceInstallStep(progress: SoundProgress?, current: CurrentSound?, targetSid: Int, idlePolls: Int = 0): VoiceInstallStep {
    if (progress != null && (progress.state == 1 || progress.state == 2)) return VoiceInstallStep.Working(progress.progress.coerceIn(0, 100))
    if (progress != null && progress.state != 3 && progress.sidInProgress > 0) {
        // Errors 2 and 13 are the plugin's "download failed"; the rest are "installation failed".
        return VoiceInstallStep.Failed(installFailed = progress.error != 2 && progress.error != 13)
    }
    if (current?.sidInUse == targetSid) return VoiceInstallStep.Done
    // The robot never started the download, so there is nothing to wait for.
    if (current != null && current.sidInProgress == 0 && idlePolls >= VOICE_INSTALL_IDLE_POLLS) return VoiceInstallStep.Failed(installFailed = false)
    return VoiceInstallStep.Working(null)
}

/** `dnld_install_sound` params as the plugin's _onPressUseButton builds them; ota_url stays empty without the cloud. */
fun installSoundParams(pack: VoicePack): JsonObject = JsonObject(
    mapOf(
        "sid" to JsonPrimitive(pack.id),
        "url" to JsonPrimitive(pack.url),
        "md5" to JsonPrimitive(pack.md5),
        "sver" to JsonPrimitive(pack.version),
        "ota_url" to JsonPrimitive(""),
    ),
)

/** The Language page's list: loading on entry, the packs, or why there are none. */
sealed interface VoicePackListState {
    data object Loading : VoicePackListState
    data class Loaded(val packs: List<VoicePack>) : VoicePackListState
    data class Unavailable(val reason: String?) : VoicePackListState
}

enum class VoiceInstallFailure { SEND, DOWNLOAD, INSTALL, TIMEOUT }

/** One "Use" press: the pack, its last progress percent, and how it ended when it failed. */
data class VoiceInstallState(val sid: Int, val percent: Int? = null, val failure: VoiceInstallFailure? = null) {
    val running: Boolean get() = failure == null
}

class VoiceInstallException(val failure: VoiceInstallFailure, cause: Throwable? = null) : RuntimeException(failure.name, cause)
