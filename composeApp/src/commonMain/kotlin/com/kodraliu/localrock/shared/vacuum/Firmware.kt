package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The robot's own OTA commands; both take an empty list. */
private val NoOtaParams = JsonArray(emptyList())

suspend fun VacuumSession.getOtaState(): V1Response = sendCommandRaw("get_ota_state", NoOtaParams)
suspend fun VacuumSession.getOtaProgress(): V1Response = sendCommandRaw("get_ota_progress", NoOtaParams)

private fun firstObject(result: JsonElement?): JsonObject? =
    (if (result is JsonArray) result.firstOrNull() else result) as? JsonObject

/** `{"ota_state": "idle|downloading|installing|wait_install|failed"}`. */
fun parseOtaState(result: JsonElement?): String? =
    (firstObject(result)?.get("ota_state") as? JsonPrimitive)?.contentOrNull

/** `{"ota_progress": 0..100}`; the robot answers 0 while it installs. */
fun parseOtaProgress(result: JsonElement?): Int? =
    (firstObject(result)?.get("ota_progress") as? JsonPrimitive)?.intOrNull

/** What the screen shows while an install runs. */
sealed interface FirmwareProgress {
    data object Idle : FirmwareProgress
    data object Starting : FirmwareProgress
    data class Downloading(val percent: Int?) : FirmwareProgress
    data object Installing : FirmwareProgress

    /** The robot dropped off while it reboots; it comes back about a minute later. */
    data object Restarting : FirmwareProgress
    data class Done(val version: String?) : FirmwareProgress
    data class Failed(val reason: String) : FirmwareProgress

    val running: Boolean
        get() = this is Starting || this is Downloading || this is Installing || this is Restarting
}

/** Charging (8), fully charged (100), or asleep on the charger (state 2 with charge_status 1). */
private val DockedStates = setOf(8, 100)
const val FIRMWARE_MIN_BATTERY = 80

/**
 * Why an install should not start now, or null when it may. The same rule the server applies, so the screen
 * explains it up front instead of the server refusing afterwards.
 */
fun firmwareInstallBlocker(state: Int?, battery: Int?, chargeStatus: Int?, inCleaning: Int?, errorCode: Int?): String? {
    if (state == null || battery == null) return "The robot has not reported its status yet."
    val docked = state in DockedStates || (state == 2 && chargeStatus == 1)
    if (!docked) return "Put the robot on its dock first. It has to be charging or resting on the charger."
    if ((inCleaning ?: 0) != 0) return "The robot is cleaning. Wait until it has finished."
    if ((errorCode ?: 0) != 0) return "The robot reports an error. Clear it before updating."
    if (battery < FIRMWARE_MIN_BATTERY) return "Charge the robot to at least $FIRMWARE_MIN_BATTERY% first (it is at $battery%)."
    return null
}

/** One look at the robot's OTA state, turned into what to show next. [seenWorking] stays true once it has left idle. */
data class FirmwareStep(val progress: FirmwareProgress, val seenWorking: Boolean, val finished: Boolean = false)

fun nextFirmwareStep(previous: FirmwareProgress, seenWorking: Boolean, answered: Boolean, otaState: String?, percent: Int?): FirmwareStep {
    if (!answered) {
        // Silence after the robot started working is the reboot; before that it says nothing yet.
        return if (seenWorking) FirmwareStep(FirmwareProgress.Restarting, true) else FirmwareStep(previous, false)
    }
    return when (otaState) {
        "downloading" -> FirmwareStep(FirmwareProgress.Downloading(percent?.takeIf { it in 0..100 }), true)
        "installing", "wait_install" -> FirmwareStep(FirmwareProgress.Installing, true)
        "failed" -> FirmwareStep(FirmwareProgress.Failed("The robot reported that the update failed."), seenWorking, finished = true)
        "idle" -> if (seenWorking) FirmwareStep(previous, true, finished = true) else FirmwareStep(previous, false)
        else -> FirmwareStep(previous, seenWorking)
    }
}
