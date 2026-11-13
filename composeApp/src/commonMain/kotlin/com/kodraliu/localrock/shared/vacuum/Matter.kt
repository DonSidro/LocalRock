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

/** The `status` values the official app reads from `matter.get_status`. */
enum class MatterState {
    KeyNotExit, KeyCorrupt, KeyDownloading, KeyDownloadSuccess, KeyDownloadFailure, Preparing, Ready, Joined, RPCError;

    /** The robot cannot hand out a setup code until the key is fetched again. */
    val failed: Boolean get() = this == KeyCorrupt || this == KeyDownloadFailure || this == RPCError

    /** A setup code and QR payload are on offer. */
    val hasCodes: Boolean get() = this == Ready || this == Joined
}

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
