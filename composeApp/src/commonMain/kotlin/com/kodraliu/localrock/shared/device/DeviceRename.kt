package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.Device
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** [duid]'s name in the latest device list, so a rename shows as soon as the list reloads; [fallback] when it is missing. */
fun currentDeviceName(devices: List<Device>, duid: String, fallback: String): String =
    devices.firstOrNull { it.duid == duid }?.name?.takeIf { it.isNotBlank() } ?: fallback

/** The longest robot name the server stores (local_roborock_server parse_device_name). */
const val MAX_DEVICE_NAME_LENGTH = 64

/** The name to send, trimmed; throws when it is empty or longer than the server keeps. */
fun validateDeviceName(name: String): String {
    val clean = name.trim()
    require(clean.isNotEmpty()) { "Enter a name" }
    require(clean.length <= MAX_DEVICE_NAME_LENGTH) { "Use $MAX_DEVICE_NAME_LENGTH characters or fewer" }
    return clean
}

// The official app renames with PUT /user/devices/<duid> and a form body name=<new name>.
suspend fun HttpClient.renameDevice(duid: String, name: String): String {
    val clean = validateDeviceName(name)
    val resp = submitForm(url = "/user/devices/$duid", formParameters = parameters { append("name", clean) }) {
        method = HttpMethod.Put
    }
    if (!resp.status.isSuccess()) {
        throw DeviceApiException(resp.status.value, "The server did not rename the robot (HTTP ${resp.status.value})")
    }
    val root = resp.body<JsonObject>()
    val code = (root["code"] as? JsonPrimitive)?.intOrNull
    if (code != null && code != 200) throw DeviceApiException(code, (root["msg"] as? JsonPrimitive)?.contentOrNull ?: "Rename failed")
    val data = (root["data"] ?: root["result"]) as? JsonObject
    val held = (data?.get("name") as? JsonPrimitive)?.contentOrNull
    // The server answers with the device as it holds it, and silently skips a name it will not take.
    if (held != null && held != clean) throw DeviceApiException(200, "The server kept the name \"$held\"")
    return clean
}
