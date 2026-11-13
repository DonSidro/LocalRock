package com.kodraliu.localrock.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What the server last heard from a robot: [GET /user/devices/{duid}/last_known]. It is what is
 * left to show when the robot has stopped answering (dead battery, lost Wi-Fi, stuck).
 */
@Serializable
data class LastKnown(
    val duid: String = "",
    val known: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("offline_at") val offlineAt: String? = null,
    val status: LastKnownStatus? = null,
    val position: LastKnownPosition? = null,
    @SerialName("has_map") val hasMap: Boolean = false,
    /** Base64 of the last map the robot sent, in the same form a live map reply decodes to. */
    val map: String? = null,
)

@Serializable
data class LastKnownStatus(
    val state: Int? = null,
    val battery: Int? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LastKnownPosition(
    val robot: LastKnownPoint? = null,
    val charger: LastKnownPoint? = null,
    @SerialName("map_updated_at") val mapUpdatedAt: String? = null,
)

/** Robot millimetres, as on the map. */
@Serializable
data class LastKnownPoint(val x: Int = 0, val y: Int = 0, val angle: Int = 0)
