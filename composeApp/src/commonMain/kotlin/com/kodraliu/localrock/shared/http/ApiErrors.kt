package com.kodraliu.localrock.shared.http

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The reply's `msg`, plus `data.reason` when the server sent one that adds something (e.g.
 * `authentication_required (stale_hawk_timestamp)`). `data` is optional and free-form, so anything
 * else is ignored.
 */
internal fun serverErrorText(msg: String, data: JsonElement?): String {
    val reason = ((data as? JsonObject)?.get("reason") as? JsonPrimitive)
        ?.takeIf { it.isString }?.content
        ?.takeIf { it.isNotEmpty() && it != msg }
        ?: return msg
    return if (msg.isEmpty()) reason else "$msg ($reason)"
}
