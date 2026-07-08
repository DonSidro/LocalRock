package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val StatusJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}


suspend fun VacuumSession.appStart(): V1Response = sendCommand("app_start")
suspend fun VacuumSession.appPause(): V1Response = sendCommand("app_pause")
suspend fun VacuumSession.appStop(): V1Response = sendCommand("app_stop")
suspend fun VacuumSession.appCharge(): V1Response = sendCommand("app_charge")

suspend fun VacuumSession.appRooms(): V1Response = sendCommand("get_room_mapping")

suspend fun VacuumSession.appSegmentClean(roomIds: List<Int>, repeat: Int = 1): V1Response {
    val segments = JsonArray(roomIds.map { JsonPrimitive(it) })
    val params = listOf(JsonObject(mapOf(
        "segments" to segments,
        "repeat" to JsonPrimitive(repeat),
    )))
    return sendCommand("app_segment_clean", params)
}

suspend fun VacuumSession.setCustomMode(level: Int): V1Response =
    sendCommand("set_custom_mode", listOf(JsonPrimitive(level)))

suspend fun VacuumSession.getConsumableStatus(): V1Response = sendCommand("get_consumable")

suspend fun VacuumSession.setWaterBoxCustomMode(level: Int): V1Response =
    sendCommand("set_water_box_custom_mode", listOf(JsonPrimitive(level)))

suspend fun VacuumSession.appStartCollectDust(): V1Response = sendCommand("app_start_collect_dust")
suspend fun VacuumSession.appStartWash(): V1Response = sendCommand("app_start_wash")
suspend fun VacuumSession.appStopWash(): V1Response = sendCommand("app_stop_wash")

suspend fun VacuumSession.getStatus(): VacuumStatus? {
    val resp = sendCommand("get_status")
    val result = resp.result ?: return null
    val obj: JsonObject = when (result) {
        is JsonArray -> result.firstOrNull()?.jsonObject ?: return null
        is JsonObject -> result
        else -> return null
    }
    return StatusJson.decodeFromJsonElement(VacuumStatus.serializer(), obj)
}

suspend fun VacuumSession.getRoomMapping(): V1Response = sendCommand("get_room_mapping")

suspend fun VacuumSession.getCleanRecord(recordId: Long): V1Response =
    sendCommand("get_clean_record", listOf(JsonPrimitive(recordId)))

suspend fun VacuumSession.getCleanSummary(): V1Response = sendCommand("get_clean_summary")

suspend fun VacuumSession.resetConsumable(field: String): V1Response =
    sendCommand("reset_consumable", listOf(JsonPrimitive(field)))

suspend fun VacuumSession.findMe(): V1Response = sendCommand("find_me")

suspend fun VacuumSession.appRcStart(): V1Response = sendCommand("app_rc_start")

suspend fun VacuumSession.appRcMove(velocity: Double, omega: Double, durationMs: Int): V1Response =
    sendCommand("app_rc_move", listOf(JsonObject(mapOf(
        "velocity" to JsonPrimitive(velocity),
        "omega" to JsonPrimitive(omega),
        "duration" to JsonPrimitive(durationMs),
    ))))

suspend fun VacuumSession.appRcEnd(): V1Response = sendCommand("app_rc_end")

suspend fun VacuumSession.appGotoTarget(x: Int, y: Int): V1Response =
    sendCommand("app_goto_target", listOf(JsonArray(listOf(JsonPrimitive(x), JsonPrimitive(y)))))

suspend fun VacuumSession.appZonedClean(zones: List<ZoneRect>): V1Response {
    val params = listOf(JsonArray(zones.map { z ->
        JsonArray(listOf(
            JsonPrimitive(z.x1), JsonPrimitive(z.y1),
            JsonPrimitive(z.x2), JsonPrimitive(z.y2),
            JsonPrimitive(z.times),
        ))
    }))
    return sendCommand("app_zoned_clean", params)
}

suspend fun VacuumSession.getTimer(): V1Response = sendCommand("get_timer")

suspend fun VacuumSession.setTimer(id: String, enabled: Boolean, cron: String): V1Response =
    sendCommand("set_timer", listOf(JsonArray(listOf(
        JsonPrimitive(id),
        JsonArray(listOf(
            JsonPrimitive(if (enabled) "on" else "off"),
            JsonArray(listOf(
                JsonPrimitive(cron),
                JsonArray(listOf(JsonPrimitive("start_clean"), JsonObject(emptyMap()))),
            )),
        )),
    ))))

suspend fun VacuumSession.delTimer(id: String): V1Response =
    sendCommand("del_timer", listOf(JsonPrimitive(id)))

suspend fun VacuumSession.updTimer(id: String, enabled: Boolean): V1Response =
    sendCommand("upd_timer", listOf(JsonArray(listOf(
        JsonPrimitive(id),
        JsonPrimitive(if (enabled) "on" else "off"),
    ))))


suspend fun VacuumSession.getSoundVolume(): V1Response = sendCommand("get_sound_volume")
suspend fun VacuumSession.setSoundVolume(volume: Int): V1Response =
    sendCommand("set_sound_volume", listOf(JsonPrimitive(volume)))

suspend fun VacuumSession.getChildLock(): V1Response = sendCommand("get_child_lock")
suspend fun VacuumSession.setChildLock(locked: Boolean): V1Response =
    sendCommand("set_child_lock", listOf(JsonObject(mapOf("lock_status" to JsonPrimitive(if (locked) 1 else 0)))))

suspend fun VacuumSession.getLedStatus(): V1Response = sendCommand("get_led_status")
suspend fun VacuumSession.setLedStatus(enabled: Boolean): V1Response =
    sendCommand("set_led_status", listOf(JsonObject(mapOf("led_status" to JsonPrimitive(if (enabled) 1 else 0)))))

suspend fun VacuumSession.getMopMode(): V1Response = sendCommand("get_mop_mode")
suspend fun VacuumSession.setMopMode(mode: Int): V1Response =
    sendCommand("set_mop_mode", listOf(JsonPrimitive(mode)))

suspend fun VacuumSession.appSetDryerStatus(on: Boolean): V1Response =
    sendCommandRaw("app_set_dryer_status", JsonObject(mapOf("status" to JsonPrimitive(if (on) 1 else 0))))

suspend fun VacuumSession.getCarpetCleanMode(): V1Response = sendCommand("get_carpet_clean_mode")
suspend fun VacuumSession.setCarpetCleanMode(mode: Int): V1Response =
    sendCommand("set_carpet_clean_mode", listOf(JsonObject(mapOf("carpet_clean_mode" to JsonPrimitive(mode)))))


suspend fun VacuumSession.getWashTowelMode(): V1Response = sendCommand("get_wash_towel_mode")
suspend fun VacuumSession.setWashTowelMode(mode: Int): V1Response =
    sendCommand("set_wash_towel_mode", listOf(JsonObject(mapOf("wash_mode" to JsonPrimitive(mode)))))

suspend fun VacuumSession.getSmartWashParams(): V1Response = sendCommand("get_smart_wash_params")
suspend fun VacuumSession.setSmartWashParams(washFreq: Int): V1Response =
    sendCommand("set_smart_wash_params", listOf(JsonObject(mapOf(
        "smart_wash" to JsonPrimitive(0),
        "wash_interval" to JsonPrimitive(washFreq + 1),
    ))))

suspend fun VacuumSession.getDustCollectionMode(): V1Response = sendCommand("get_dust_collection_mode")
suspend fun VacuumSession.setDustCollectionMode(mode: Int): V1Response =
    sendCommand("set_dust_collection_mode", listOf(JsonObject(mapOf("mode" to JsonPrimitive(mode)))))


suspend fun VacuumSession.getDndTimer(): V1Response = sendCommand("get_dnd_timer")
suspend fun VacuumSession.setDndTimer(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): V1Response =
    sendCommand("set_dnd_timer", listOf(JsonObject(mapOf(
        "start_hour" to JsonPrimitive(startHour),
        "start_minute" to JsonPrimitive(startMinute),
        "end_hour" to JsonPrimitive(endHour),
        "end_minute" to JsonPrimitive(endMinute),
    ))))
suspend fun VacuumSession.closeDndTimer(): V1Response = sendCommand("close_dnd_timer")


suspend fun VacuumSession.getMultiMapsList(): V1Response = sendCommand("get_multi_maps_list")
suspend fun VacuumSession.loadMultiMap(mapFlag: Int): V1Response =
    sendCommand("load_multi_map", listOf(JsonPrimitive(mapFlag)))


suspend fun VacuumSession.checkHomesecPassword(passwordMd5Hex: String): V1Response =
    sendCommandRaw("check_homesec_password", JsonObject(mapOf("password" to JsonPrimitive(passwordMd5Hex))))

suspend fun VacuumSession.setHomesecPassword(passwordMd5Hex: String): V1Response =
    sendCommandRaw("set_homesec_password", JsonObject(mapOf("password" to JsonPrimitive(passwordMd5Hex))))

suspend fun VacuumSession.resetHomesecPassword(): V1Response = sendCommand("reset_homesec_password")

suspend fun VacuumSession.getHomesecConnectStatus(): V1Response =
    sendCommand("get_homesec_connect_status")

suspend fun VacuumSession.startCameraPreview(clientIdHex: String, passwordMd5Hex: String): V1Response =
    sendCommandRaw("start_camera_preview", JsonObject(mapOf(
        "client_id" to JsonPrimitive(clientIdHex),
        "quality" to JsonPrimitive("HD"),
        "password" to JsonPrimitive(passwordMd5Hex),
    )))

suspend fun VacuumSession.stopCameraPreview(clientIdHex: String): V1Response =
    sendCommandRaw("stop_camera_preview", JsonObject(mapOf("client_id" to JsonPrimitive(clientIdHex))))

suspend fun VacuumSession.getTurnServer(): V1Response = sendCommand("get_turn_server")

suspend fun VacuumSession.sendSdpToRobot(appSdpBase64: String): V1Response =
    sendCommandRaw("send_sdp_to_robot", JsonObject(mapOf("app_sdp" to JsonPrimitive(appSdpBase64))))

suspend fun VacuumSession.getDeviceSdp(): V1Response = sendCommand("get_device_sdp")

suspend fun VacuumSession.sendIceToRobot(appIceBase64: String): V1Response =
    sendCommandRaw("send_ice_to_robot", JsonObject(mapOf("app_ice" to JsonPrimitive(appIceBase64))))

suspend fun VacuumSession.getDeviceIce(): V1Response = sendCommand("get_device_ice")
