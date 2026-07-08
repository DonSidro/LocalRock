package com.kodraliu.localrock.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Scene(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val type: String,
    val homeId: Long,
    val deviceId: String,
    val deviceName: String,
    val param: String,
    val extra: String? = null,
)
