package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** `newFeatureSet` bit for Matter (python-roborock NewFeatureStrBit.MATTER_SUPPORT_FLAG). */
const val MATTER_FEATURE_BIT = 67

/** Bits that mark a robot able to patrol and photograph pets (python-roborock names). */
const val VIDEO_PATROL_FEATURE_BIT = 48
const val PET_SNAPSHOT_FEATURE_BIT = 50

fun isMatterSupported(newFeatureSet: String?): Boolean = newFeatureBit(newFeatureSet, MATTER_FEATURE_BIT)

fun isPetSearchSupported(newFeatureSet: String?): Boolean =
    newFeatureBit(newFeatureSet, VIDEO_PATROL_FEATURE_BIT) || newFeatureBit(newFeatureSet, PET_SNAPSHOT_FEATURE_BIT)

/** The `status` values the official app reads from `matter.get_status`. */
enum class MatterState {
    KeyNotExit, KeyCorrupt, KeyDownloading, KeyDownloadSuccess, KeyDownloadFailure, Preparing, Ready, Joined, RPCError;

    /** The robot cannot hand out a setup code until the key is fetched again. */
    val failed: Boolean get() = this == KeyCorrupt || this == KeyDownloadFailure || this == RPCError

    /** A setup code and QR payload are on offer. */
    val hasCodes: Boolean get() = this == Ready || this == Joined

    // Joined shows no code and no renew action in the official app (decompile: a joined card, plus reset); re-sending the key may drop the pairing.
    val canRenewCode: Boolean get() = this == Ready
}

/** Asking for a key while joined may drop the pairing, so only a robot that has not joined is asked. */
fun matterKeyRequestAllowed(state: MatterState?): Boolean = state != MatterState.Joined

/** Whether the page should send matter.dnld_key after reading [state]. */
fun matterNeedsKey(state: MatterState, attempt: Int, renewing: Boolean, asked: Boolean): Boolean =
    matterKeyRequestAllowed(state) &&
        (state == MatterState.KeyNotExit || (attempt > 0 && state.failed) || (renewing && !asked))

/** A renewing page keeps polling while the robot still shows the code it had, for a few polls. */
fun matterCodeStillOld(renewing: Boolean, state: MatterState, unchangedCode: Boolean, pollsSinceAsk: Int): Boolean =
    renewing && state.hasCodes && unchangedCode && pollsSinceAsk < MATTER_OLD_CODE_POLLS

/** True when the page can stop polling and show what it read. */
fun matterPollIsDone(state: MatterState, renewing: Boolean, asked: Boolean, unchangedCode: Boolean, pollsSinceAsk: Int): Boolean =
    state == MatterState.Joined ||
        (state.hasCodes && !(renewing && !asked) && !matterCodeStillOld(renewing, state, unchangedCode, pollsSinceAsk))

/** The countdown starts only for a code the page saw appear, and never for one that did not change on a renewal. */
fun matterCodeAgeKnown(state: MatterState, sawWorking: Boolean, renewing: Boolean, asked: Boolean, unchangedCode: Boolean): Boolean =
    state != MatterState.Joined && (sawWorking || renewing || asked) && !(renewing && unchangedCode)

const val MATTER_OLD_CODE_POLLS = 4

data class MatterInfo(val state: MatterState?, val manualCode: String?, val qrPayload: String?)

/** Reads the `matter.get_status` reply: an object, or a one-item list holding it. */
fun parseMatterStatus(result: JsonElement?): MatterInfo? {
    val obj = (if (result is JsonArray) result.firstOrNull() else result) as? JsonObject ?: return null
    fun text(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    val state = text("status")?.let { name -> MatterState.entries.firstOrNull { it.name == name } }
    return MatterInfo(state, text("ManualPairingCode"), text("QrCode"))
}

/** The three Matter commands take an empty object, not an empty list. */
private val NoParams = JsonObject(emptyMap())

suspend fun VacuumSession.getMatterStatus(): V1Response = sendCommandRaw("matter.get_status", NoParams)
suspend fun VacuumSession.downloadMatterKey(): V1Response = sendCommandRaw("matter.dnld_key", NoParams)
suspend fun VacuumSession.resetMatter(): V1Response = sendCommandRaw("matter.reset", NoParams)

/** Find my Pet: the robot goes looking and reports found or not found as an event. */
suspend fun VacuumSession.startPetSearch(): V1Response = sendCommandRaw("app_start_pet_patrol", NoParams)
