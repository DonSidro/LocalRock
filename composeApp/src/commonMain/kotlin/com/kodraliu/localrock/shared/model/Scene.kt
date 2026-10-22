package com.kodraliu.localrock.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A routine ("scene") saved with the official app, as local_roborock_server lists it. The steps in
 * [param] are the official app's own JSON; the server runs them, so the app only shows the name.
 */
@Serializable
data class Scene(
    val id: Long,
    val name: String,
    val enabled: Boolean = true,
    val type: String? = null,
    val homeId: Long? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val param: JsonElement? = null,
    val extra: JsonElement? = null,
)

/**
 * The server's answer to running a routine. Running the routine that is already going stops it
 * ("cancel_requested"); another routine on the same robot blocks it ("routine_in_progress").
 */
@Serializable
data class SceneRunResult(
    val accepted: Boolean = true,
    val status: String? = null,
    val sceneName: String? = null,
    val activeSceneName: String? = null,
)
