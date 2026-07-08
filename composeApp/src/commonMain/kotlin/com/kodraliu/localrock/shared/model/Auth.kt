package com.kodraliu.localrock.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val uid: Long,
    val token: String,
    val rruid: String,
    val rriot: Rriot,

    val tokentype: String? = null,
    val region: String? = null,
    val countrycode: String? = null,
    val country: String? = null,
    val nickname: String? = null,

    @SerialName("tuya_device_state")
    val tuyaDeviceState: Int? = null,

    val avatarurl: String? = null,
)

@Serializable
data class Rriot(
    val u: String,
    val s: String,
    val h: String,
    val k: String,
    val r: RegionInfo,
)

@Serializable
data class RegionInfo(
    val r: String,
    val a: String,
    val m: String,
    val l: String,
)