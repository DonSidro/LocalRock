package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.coroutines.delay

/** One `start_camera_preview` failure in the official app's terms: its own code and the fix text it shows. */
data class CameraStartError(val appCode: Int, val message: String) {
    val retryable: Boolean get() = appCode in CameraStartRetry.RETRYABLE
    fun display(): String = "$message (error $appCode)"
}

object CameraStartErrors {
    const val UNKNOWN = -100
    const val IN_CHARGE = -101
    const val CAMERA_STATUS = -102
    const val INNER_UNKNOWN = -103
    const val ALREADY_IN_PREVIEW = -104
    const val PASSWORD = -105
    const val PASSWORD_FREQUENT = -106
    const val DISCONNECTING = -107
    const val TURN_FAILED = -108
    const val PREVIEW_TIMEOUT = -114
    const val DUST_COLLECTION = -116
    const val RETRIES_EXHAUSTED = -117

    private const val RESET_ROBOT = "Please reset the robot and try again."
    private const val INNER_UNKNOWN_TEXT =
        "Please disable and then re-enable the remote viewing function, or restart the device and try again."

    /** Maps the robot's error code (null when the reply carried none) to the official app's error and text. */
    fun fromRobotCode(code: Int?): CameraStartError = when (code) {
        null -> CameraStartError(UNKNOWN, "Network failed. Please check the robot's network and try again.")
        -10009 -> CameraStartError(PASSWORD, RESET_ROBOT)
        -10010 -> CameraStartError(PASSWORD_FREQUENT, RESET_ROBOT)
        -10011 -> CameraStartError(IN_CHARGE, "Please move the device away from the charging dock to view the video.")
        -10012 -> CameraStartError(CAMERA_STATUS, "Camera error. Please restart the robot and try again.")
        -10013 -> CameraStartError(
            ALREADY_IN_PREVIEW,
            "Please check if the video is being displayed on another phone. If not, please wait a minute and try again.",
        )
        -10014 -> CameraStartError(TURN_FAILED, "Network connection error. Please check your network and try again.")
        -10015 -> CameraStartError(INNER_UNKNOWN, INNER_UNKNOWN_TEXT)
        -10016 -> CameraStartError(DISCONNECTING, "Resource occupied. Please try again later.")
        -10021 -> CameraStartError(DUST_COLLECTION, "Unable to remote viewing. Emptying in progress.")
        else -> CameraStartError(INNER_UNKNOWN, INNER_UNKNOWN_TEXT)
    }

    val retriesExhausted = CameraStartError(RETRIES_EXHAUSTED, "The maximum number of retries has been reached.")
}

/** The official app's start retry rule: transient errors are retried 500 ms apart, then -117. */
object CameraStartRetry {
    const val MAX_RETRIES = 8
    const val DELAY_MS = 500L

    /** -100, -103, -114 and robot-is-disconnecting (-107). */
    val RETRYABLE = setOf(
        CameraStartErrors.UNKNOWN,
        CameraStartErrors.INNER_UNKNOWN,
        CameraStartErrors.PREVIEW_TIMEOUT,
        CameraStartErrors.DISCONNECTING,
    )

    /** What one attempt did: succeeded, or the robot code it failed with (null when the reply had none). */
    sealed interface Attempt {
        data object Ok : Attempt
        data class Refused(val robotCode: Int?) : Attempt
    }

    /** Null on success, otherwise the final error: the first non-retryable one, or -117 after [MAX_RETRIES] retries. */
    suspend fun run(pause: suspend (Long) -> Unit = { delay(it) }, attempt: suspend () -> Attempt): CameraStartError? {
        var retries = 0
        while (true) {
            val result = attempt()
            if (result is Attempt.Ok) return null
            val error = CameraStartErrors.fromRobotCode((result as Attempt.Refused).robotCode)
            if (!error.retryable) return error
            if (retries >= MAX_RETRIES) return CameraStartErrors.retriesExhausted
            retries++
            pause(DELAY_MS)
        }
    }
}
