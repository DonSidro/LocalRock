package com.kodraliu.localrock.shared.model

import kotlinx.serialization.Serializable


@Serializable
data class FirmwareOtaResponse(
    val code: Int = 0,
    val msg: String = "",
    val result: FirmwareUpdateInfo? = null,
)

@Serializable
data class FirmwareUpdateInfo(
    /** Latest available firmware version. */
    val version: String? = null,
    /** Version currently installed on the device (per the cloud). */
    val currentVersion: String? = null,
    /** True when [version] is newer than [currentVersion] and an update can be applied. */
    val updatable: Boolean = false,
    /** Release notes / changelog. */
    val desc: String? = null,
    val releaseTime: String? = null,
    /** Mandatory update the device will apply regardless of user action. */
    val forceUpdate: Boolean = false,
)
