package com.kodraliu.localrock.shared.model

import kotlinx.serialization.Serializable


@Serializable
data class HomeDetail(
    val id: Long? = null,
    val name: String? = null,
    val rrHomeId: Long? = null,
    val rrHomeName: String? = null,
    val tuyaHomeId: Long? = null,
    val homeId: Long? = null,
    val deviceListOrder: List<String> = emptyList(),
) {
    val resolvedHomeId: Long?
        get() = rrHomeId ?: id ?: homeId
}
