package com.kodraliu.localrock.shared.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement


@Serializable
data class ApiResponse<T>(
    val success: Boolean = true,
    val code: Int,
    val msg: String,
    val data: T,
    val result: T? = null,
)

@Serializable
data class AppInfo(
    val stored: Boolean,
    val pushChannel: String,
    val channelToken: String,
    val locale: String,
    val lang: String,
    val osType: String,
)

@Serializable
data class InboxLatest(
    val count: Int,
    val hasUnread: Boolean,
    val latest: JsonElement? = null,
    val updatedAt: Long,
)

@Serializable
data class CodeSendData(
    val sent: Boolean = false,
    val validForSec: Int? = null,
)

@Serializable
data class CodeValidateData(
    val valid: Boolean = false,
)
