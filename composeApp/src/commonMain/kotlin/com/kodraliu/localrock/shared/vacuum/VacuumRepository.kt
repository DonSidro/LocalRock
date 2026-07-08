package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.model.Room
import com.kodraliu.localrock.shared.mqtt.MqttClient
import com.kodraliu.localrock.shared.mqtt.MqttTopics
import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.protocol.saveDebugBlob
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.map.parseB01Map
import com.kodraliu.localrock.shared.vacuum.map.parseLegacyMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull


class VacuumRepository(
    duid: String,
    localKey: String,
    private val rriotKey: String,
    private val mqttClient: MqttClient,
    topics: MqttTopics,
    private val nowEpochSeconds: () -> Int,
    private val pollIntervalMs: Long = 5_000L,
    private val demo: Boolean = false,
) {
    val session: VacuumSession = VacuumSession(
        localKey = localKey,
        mqtt = mqttClient,
        topics = topics,
        nowEpochSeconds = nowEpochSeconds,
        demo = demo,
    )
    val duid: String = duid

    private val _status = MutableStateFlow(VacuumStatus())
    val status: StateFlow<VacuumStatus> = _status

    private val _mapBytes = MutableStateFlow<ByteArray?>(null)
    val mapBytes: StateFlow<ByteArray?> = _mapBytes

    private val _parsedMap = MutableStateFlow<ParsedMap?>(null)
    val parsedMap: StateFlow<ParsedMap?> = _parsedMap

    private val _mapStatus = MutableStateFlow("idle")
    val mapStatus: StateFlow<String> = _mapStatus

    private val _rooms = MutableStateFlow<List<ParsedMapRoom>>(emptyList())
    val rooms: StateFlow<List<ParsedMapRoom>> = _rooms


    private var homeRoomNames: Map<Long, String> = emptyMap()   // roomId -> name
    private var segmentToRoomId: Map<Int, Long> = emptyMap()     // map segment id -> roomId

    private val _consumableStatus = MutableStateFlow<ConsumableStatus?>(null)
    val consumableStatus: StateFlow<ConsumableStatus?> = _consumableStatus

    private val _consumableError = MutableStateFlow<String?>(null)
    val consumableError: StateFlow<String?> = _consumableError

    private val _cleanHistory = MutableStateFlow<List<CleanRecord>>(emptyList())
    val cleanHistory: StateFlow<List<CleanRecord>> = _cleanHistory

    private val _timers = MutableStateFlow<List<CleanTimer>>(emptyList())
    val timers: StateFlow<List<CleanTimer>> = _timers

    private val _robotSettings = MutableStateFlow(RobotSettings())
    val robotSettings: StateFlow<RobotSettings> = _robotSettings

    private val _dockSettings = MutableStateFlow(DockSettings())
    val dockSettings: StateFlow<DockSettings> = _dockSettings

    private val _cleanSummary = MutableStateFlow<CleanSummary?>(null)
    val cleanSummary: StateFlow<CleanSummary?> = _cleanSummary

    private val _dndSettings = MutableStateFlow<DndSettings?>(null)
    val dndSettings: StateFlow<DndSettings?> = _dndSettings

    private val _floorMaps = MutableStateFlow<List<FloorMap>>(emptyList())
    val floorMaps: StateFlow<List<FloorMap>> = _floorMaps

    private val _currentFloorFlag = MutableStateFlow(0)
    val currentFloorFlag: StateFlow<Int> = _currentFloorFlag

    private val _mopMode = MutableStateFlow<Int?>(null)
    val mopMode: StateFlow<Int?> = _mopMode

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var dpsJob: Job? = null
    private var mapPollJob: Job? = null

    suspend fun attach() {
        check(pollJob == null) { "Already attached" }
        if (demo) {
            seedDemo()
            return
        }
        println("[VacLocal] attach duid=$duid")
        session.start()
        dpsJob = scope.launch {
            session.dpsUpdates.collect { dps ->
                println("[VacLocal] dps push: ${dps.keys}")
                _status.update { it.applyDpsPush(dps) }
            }
        }
        pollJob = scope.launch {

            var consecutiveFailures = 0
            while (isActive) {
                runCatching { session.getStatus() }
                    .onSuccess { fresh ->
                        consecutiveFailures = 0
                        if (fresh != null) {
                            println("[VacLocal] get_status ok: state=${fresh.state} battery=${fresh.battery}")
                            _status.update { it.merge(fresh) }
                        } else {
                            println("[VacLocal] get_status returned null (empty result)")
                        }
                    }
                    .onFailure { e ->
                        consecutiveFailures++
                        println("[VacLocal] get_status FAILED (#$consecutiveFailures): ${e::class.simpleName}: ${e.message}")
                        if (consecutiveFailures >= STALE_LINK_FAILURE_THRESHOLD) {
                            println("[VacLocal] $consecutiveFailures consecutive poll failures — forcing MQTT reconnect")
                            runCatching { mqttClient.forceReconnect() }
                            consecutiveFailures = 0
                        }
                    }
                delay(pollIntervalMs)
            }
        }
        mapPollJob = scope.launch {
            runCatching { fetchMap() }.onFailure { println("[VacLocal] fetchMap FAILED: ${it::class.simpleName}: ${it.message}") }
            while (isActive) {
                val fastPoll = _status.value.state in ACTIVE_STATES
                delay(if (fastPoll) MAP_FAST_POLL_MS else MAP_IDLE_POLL_MS)
                runCatching { fetchMap() }
            }
        }
    }


    suspend fun forceReconnect() {
        runCatching { mqttClient.forceReconnect() }
    }


    suspend fun refreshStatus() {
        runCatching { session.getStatus() }.getOrNull()
            ?.let { fresh -> _status.update { it.merge(fresh) } }
    }

    fun detach() {
        pollJob?.cancel(); pollJob = null
        dpsJob?.cancel(); dpsJob = null
        mapPollJob?.cancel(); mapPollJob = null
        scope.launch { session.close() }
    }

    suspend fun clean(): V1Response = session.appStart()
    suspend fun pause(): V1Response = session.appPause()
    suspend fun stopCleaning(): V1Response = session.appStop()
    suspend fun dock(): V1Response = session.appCharge()


    suspend fun rooms(): V1Response {
        val resp = session.appRooms()
        val result = resp.result
        if (result is JsonArray) {
            val mapping = result.mapNotNull { entry ->
                if (entry is JsonArray && entry.size >= 2) {
                    val segment = entry[0].jsonPrimitive.intOrNull ?: return@mapNotNull null
                    val roomId = entry[1].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
                    segment to roomId
                } else null
            }.toMap()
            if (mapping.isNotEmpty()) {
                segmentToRoomId = mapping
                println("[VacLocal] room mapping seg->roomId=$mapping resolved=${mapping.mapValues { homeRoomNames[it.value] ?: "(no home name)" }}")
                applyRoomNames()
            }
        }
        return resp
    }


    fun setHomeRooms(rooms: List<Room>) {
        homeRoomNames = rooms.associate { it.id to it.name }
        println("[VacLocal] home rooms (roomId->name)=$homeRoomNames")
        applyRoomNames()
    }

    private fun resolveRoomName(room: ParsedMapRoom): String =
        segmentToRoomId[room.id]?.let { homeRoomNames[it] } ?: room.name

    private fun applyRoomNames() {
        _parsedMap.update { pm ->
            pm?.copy(rooms = pm.rooms.map { it.copy(name = resolveRoomName(it)) })
        }
        _rooms.update { list -> list.map { it.copy(name = resolveRoomName(it)) } }
    }

    suspend fun cleanRooms(roomIds: List<Int>, repeat: Int = 1): V1Response =
        session.appSegmentClean(roomIds, repeat)

    suspend fun setFanPower(level: Int): V1Response = session.setCustomMode(level)

    suspend fun setWaterBoxMode(level: Int): V1Response = session.setWaterBoxCustomMode(level)

    suspend fun collectDust(): V1Response = session.appStartCollectDust()
    suspend fun washMop(): V1Response = session.appStartWash()
    suspend fun stopWash(): V1Response = session.appStopWash()
    suspend fun startDryer(): V1Response = session.appSetDryerStatus(true)

    suspend fun stopDryer(): V1Response = session.appSetDryerStatus(false)

    suspend fun fetchMopMode() {
        runCatching { session.getMopMode() }.getOrNull()
            ?.result?.let { r ->
                val mode = when (r) {
                    is JsonArray -> (r.firstOrNull() as? JsonPrimitive)?.intOrNull
                    is JsonPrimitive -> r.intOrNull
                    else -> null
                }
                if (mode != null) _mopMode.value = mode
            }
    }

    suspend fun setMopMode(mode: Int) {
        session.setMopMode(mode)
        _mopMode.value = mode
    }

    suspend fun fetchConsumableStatus() {
        val resp = session.getConsumableStatus()
        val result = resp.result ?: return
        val obj: JsonObject = when (result) {
            is JsonArray -> result.firstOrNull()?.jsonObject ?: return
            is JsonObject -> result
            else -> return
        }
        _consumableStatus.value = ConsumableJson.decodeFromJsonElement(ConsumableStatus.serializer(), obj)
    }


    suspend fun fetchCleanHistory() {
        val ids = fetchCleanSummary()
        val records = mutableListOf<CleanRecord>()
        for (id in ids.take(MAX_HISTORY_RECORDS)) {
            val result = runCatching { session.getCleanRecord(id) }.getOrNull()?.result ?: continue
            parseCleanRecord(result, id)?.let { records += it }
        }
        _cleanHistory.value = records
    }


    private fun parseCleanRecord(result: JsonElement, recordId: Long): CleanRecord? {
        val element = if (result is JsonArray) result.firstOrNull() ?: return null else result
        return when (element) {
            is JsonArray -> {
                if (element.size < 6) return null
                CleanRecord(
                    beginEpoch = (element[0] as? JsonPrimitive)?.longOrNull ?: recordId,
                    endEpoch = (element[1] as? JsonPrimitive)?.longOrNull ?: 0L,
                    durationSeconds = (element[2] as? JsonPrimitive)?.longOrNull ?: 0L,
                    areaMm2 = (element[3] as? JsonPrimitive)?.longOrNull ?: 0L,
                    errorCode = (element[4] as? JsonPrimitive)?.intOrNull ?: 0,
                    complete = (element[5] as? JsonPrimitive)?.intOrNull ?: 0,
                )
            }
            is JsonObject -> CleanRecord(
                beginEpoch = element["begin"]?.jsonPrimitive?.longOrNull ?: recordId,
                endEpoch = element["end"]?.jsonPrimitive?.longOrNull ?: 0L,
                durationSeconds = element["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                areaMm2 = element["area"]?.jsonPrimitive?.longOrNull ?: 0L,
                errorCode = element["error"]?.jsonPrimitive?.intOrNull ?: 0,
                complete = element["complete"]?.jsonPrimitive?.intOrNull ?: 0,
            )
            else -> null
        }
    }

    suspend fun resetConsumable(field: String) { session.resetConsumable(field) }

    suspend fun findMe() { session.findMe() }

    suspend fun startRemoteControl() { session.appRcStart() }

    suspend fun moveRemote(velocity: Double, omega: Double, durationMs: Int) {
        session.appRcMove(velocity, omega, durationMs)
    }

    suspend fun stopRemoteControl() { session.appRcEnd() }

    suspend fun gotoTarget(x: Int, y: Int) { session.appGotoTarget(x, y) }

    suspend fun cleanZones(zones: List<ZoneRect>) { session.appZonedClean(zones) }

    suspend fun fetchTimers() {
        val resp = session.getTimer()
        val result = resp.result ?: return
        if (result !is JsonArray) return
        val parsed = result.mapNotNull { entry ->
            if (entry !is JsonArray || entry.size < 3) return@mapNotNull null
            val id = entry[0].jsonPrimitive.contentOrNull ?: return@mapNotNull null
            val enabled = entry[1].jsonPrimitive.contentOrNull == "on"
            val details = runCatching { entry[2].jsonArray }.getOrNull() ?: return@mapNotNull null
            val cron = details.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            parseCronTimer(id, enabled, cron)
        }
        _timers.value = parsed
    }

    suspend fun createTimer(hour: Int, minute: Int, weekdays: Set<Int>): CleanTimer {
        val id = nowEpochSeconds().toString()
        val cron = buildCron(minute, hour, weekdays)
        session.setTimer(id, enabled = true, cron = cron)
        val timer = CleanTimer(id = id, enabled = true, hour = hour, minute = minute, weekdays = weekdays)
        _timers.value = _timers.value + timer
        return timer
    }

    suspend fun toggleTimer(timer: CleanTimer) {
        val updated = timer.copy(enabled = !timer.enabled)
        session.updTimer(timer.id, updated.enabled)
        _timers.value = _timers.value.map { if (it.id == timer.id) updated else it }
    }

    suspend fun deleteTimer(timer: CleanTimer) {
        session.delTimer(timer.id)
        _timers.value = _timers.value.filter { it.id != timer.id }
    }

    suspend fun fetchRobotSettings() {
        runCatching { session.getSoundVolume() }.getOrNull()
            ?.result?.jsonPrimitive?.intOrNull
            ?.let { _robotSettings.update { s -> s.copy(volume = it) } }
        runCatching { session.getChildLock() }.getOrNull()
            ?.result?.jsonObject?.get("lock_status")?.jsonPrimitive?.intOrNull
            ?.let { _robotSettings.update { s -> s.copy(childLock = it == 1) } }
        runCatching { session.getLedStatus() }.getOrNull()
            ?.result?.jsonObject?.get("led_status")?.jsonPrimitive?.intOrNull
            ?.let { _robotSettings.update { s -> s.copy(ledEnabled = it == 1) } }
        runCatching { session.getCarpetCleanMode() }.getOrNull()
            ?.result?.jsonObject?.get("carpet_clean_mode")?.jsonPrimitive?.intOrNull
            ?.let { _robotSettings.update { s -> s.copy(carpetMode = it) } }
    }

    suspend fun setVolume(volume: Int) {
        session.setSoundVolume(volume)
        _robotSettings.update { it.copy(volume = volume) }
    }

    suspend fun setChildLock(locked: Boolean) {
        session.setChildLock(locked)
        _robotSettings.update { it.copy(childLock = locked) }
    }

    suspend fun setLed(enabled: Boolean) {
        session.setLedStatus(enabled)
        _robotSettings.update { it.copy(ledEnabled = enabled) }
    }

    suspend fun setCarpetMode(mode: Int) {
        session.setCarpetCleanMode(mode)
        _robotSettings.update { it.copy(carpetMode = mode) }
    }

    suspend fun fetchDockSettings() {
        runCatching { session.getWashTowelMode() }.getOrNull()
            ?.result?.jsonObject?.get("wash_mode")?.jsonPrimitive?.intOrNull
            ?.let { _dockSettings.update { s -> s.copy(washMode = it) } }
        runCatching { session.getSmartWashParams() }.getOrNull()
            ?.result?.jsonObject?.get("wash_interval")?.jsonPrimitive?.intOrNull
            ?.let { interval -> _dockSettings.update { s -> s.copy(washFreq = (interval - 1).coerceAtLeast(0)) } }
        runCatching { session.getDustCollectionMode() }.getOrNull()
            ?.result?.jsonObject?.get("mode")?.jsonPrimitive?.intOrNull
            ?.let { _dockSettings.update { s -> s.copy(autoEmptyMode = it) } }
    }

    suspend fun setWashMode(mode: Int) {
        session.setWashTowelMode(mode)
        _dockSettings.update { it.copy(washMode = mode) }
    }

    suspend fun setWashFreq(freq: Int) {
        session.setSmartWashParams(freq)
        _dockSettings.update { it.copy(washFreq = freq) }
    }

    suspend fun setAutoEmptyMode(mode: Int) {
        session.setDustCollectionMode(mode)
        _dockSettings.update { it.copy(autoEmptyMode = mode) }
    }


    suspend fun fetchCleanSummary(): List<Long> {
        val result = session.getCleanSummary().result ?: return emptyList()
        return when (result) {
            is JsonArray -> {
                if (result.size < 3) return emptyList()
                _cleanSummary.value = CleanSummary(
                    totalTimeSec = (result[0] as? JsonPrimitive)?.longOrNull ?: 0L,
                    totalAreaMm2 = (result[1] as? JsonPrimitive)?.longOrNull ?: 0L,
                    totalCount = (result[2] as? JsonPrimitive)?.intOrNull ?: 0,
                )
                (result.getOrNull(3) as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                    ?: emptyList()
            }
            is JsonObject -> {
                _cleanSummary.value = CleanSummary(
                    totalTimeSec = result["clean_time"]?.jsonPrimitive?.longOrNull ?: 0L,
                    totalAreaMm2 = result["clean_area"]?.jsonPrimitive?.longOrNull ?: 0L,
                    totalCount = result["clean_count"]?.jsonPrimitive?.intOrNull ?: 0,
                )
                (result["records"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }


    suspend fun fetchDndSettings() {
        val resp = session.getDndTimer()
        val obj = resp.result?.let { r ->
            when (r) {
                is JsonArray -> r.firstOrNull()?.jsonObject
                is JsonObject -> r
                else -> null
            }
        } ?: return
        _dndSettings.value = DndSettings(
            enabled = obj["enabled"]?.jsonPrimitive?.intOrNull == 1,
            startHour = obj["start_hour"]?.jsonPrimitive?.intOrNull ?: 22,
            startMinute = obj["start_minute"]?.jsonPrimitive?.intOrNull ?: 0,
            endHour = obj["end_hour"]?.jsonPrimitive?.intOrNull ?: 8,
            endMinute = obj["end_minute"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    suspend fun setDnd(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        session.setDndTimer(startHour, startMinute, endHour, endMinute)
        _dndSettings.update {
            it?.copy(enabled = true, startHour = startHour, startMinute = startMinute, endHour = endHour, endMinute = endMinute)
                ?: DndSettings(true, startHour, startMinute, endHour, endMinute)
        }
    }

    suspend fun disableDnd() {
        session.closeDndTimer()
        _dndSettings.update { it?.copy(enabled = false) }
    }


    suspend fun fetchFloorMaps() {
        val resp = session.getMultiMapsList()
        val obj = resp.result?.let { r ->
            when (r) {
                is JsonArray -> r.firstOrNull()?.jsonObject
                is JsonObject -> r
                else -> null
            }
        } ?: return
        val mapInfoArr = obj["map_info"]?.jsonArray ?: return
        _floorMaps.value = mapInfoArr.mapNotNull { elem ->
            val o = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val flag = o["mapFlag"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            FloorMap(name, flag)
        }
    }

    suspend fun switchFloor(mapFlag: Int) {
        session.loadMultiMap(mapFlag)
        _currentFloorFlag.value = mapFlag
        _parsedMap.value = null
        _mapBytes.value = null
        runCatching { fetchMap() }
    }


    /** Seeds fabricated state for offline demo mode; no MQTT session is started. */
    private fun seedDemo() {
        _status.value = com.kodraliu.localrock.shared.demo.DemoData.status
        _consumableStatus.value = com.kodraliu.localrock.shared.demo.DemoData.consumable
        _cleanSummary.value = com.kodraliu.localrock.shared.demo.DemoData.cleanSummary
        _mapStatus.value = "Demo mode — live map unavailable"
    }

    suspend fun fetchMap(): MapResponse {
        if (demo) {
            _mapStatus.value = "Demo mode — live map unavailable"
            return MapResponse(requestId = 0, data = ByteArray(0))
        }
        _mapStatus.value = "requesting…"
        val sd = createSecurityData(rriotKey)
        try {
            val resp = session.fetchMap(sd, timeoutMs = 60_000L)
            _mapBytes.value = resp.data
            val sizeKb = resp.data.size / 1024
            // Try B01 first (Q-series / newer firmware), then legacy "rr"-magic (S-series).
            val b01 = runCatching { parseB01Map(resp.data) }
            val parsed = b01.getOrNull()
                ?: runCatching { parseLegacyMap(resp.data) }.getOrNull()
            _parsedMap.value = parsed
            if (parsed != null && parsed.rooms.isNotEmpty()) {
                _rooms.value = parsed.rooms
            }
            applyRoomNames()
            if (parsed == null) {
                val first8 = resp.data.take(8).joinToString("") {
                    val v = it.toInt() and 0xff
                    "${HEX[v ushr 4]}${HEX[v and 0x0f]}"
                }
                val savedPath = saveDebugBlob("vaclocal-map-${duid}-${nowEpochSeconds()}.bin", resp.data)
                val saved = savedPath?.let { " • saved → $it" } ?: ""
                _mapStatus.value = "ok: $sizeKb KB, no parser matched — head=$first8$saved"
            } else {
                val format = if (b01.isSuccess) "B01" else "legacy"
                _mapStatus.value = "ok: $sizeKb KB, ${parsed.width}×${parsed.height} $format"
            }
            return resp
        } catch (e: Throwable) {
            _mapStatus.value = "failed: ${e::class.simpleName}: ${e.message ?: "(no message)"}"
            throw e
        }
    }

    private companion object {
        val HEX = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
        val ConsumableJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
        const val MAP_FAST_POLL_MS = 2_000L
        const val MAP_IDLE_POLL_MS = 30_000L
        const val MAX_HISTORY_RECORDS = 20

        const val STALE_LINK_FAILURE_THRESHOLD = 3
        val ACTIVE_STATES = setOf(
            VacuumStateCodes.STARTING,
            VacuumStateCodes.CLEANING,
            VacuumStateCodes.RETURNING_HOME,
            VacuumStateCodes.MANUAL_MODE,
            VacuumStateCodes.PAUSED,
            VacuumStateCodes.SPOT_CLEANING,
            VacuumStateCodes.DOCKING,
            VacuumStateCodes.GOING_TO_TARGET,
            VacuumStateCodes.ZONED_CLEANING,
            VacuumStateCodes.SEGMENT_CLEANING,
        )
    }
}

private fun buildCron(minute: Int, hour: Int, weekdays: Set<Int>): String {
    val days = if (weekdays.isEmpty()) "*" else weekdays.sorted().joinToString(",")
    return "$minute $hour * * $days"
}

private fun parseCronTimer(id: String, enabled: Boolean, cron: String): CleanTimer? {
    val parts = cron.trim().split(Regex("\\s+"))
    if (parts.size < 5) return null
    val minute = parts[0].toIntOrNull() ?: return null
    val hour = parts[1].toIntOrNull() ?: return null
    val weekdays = if (parts[4] == "*") emptySet()
    else parts[4].split(",").mapNotNull { it.toIntOrNull() }.toSet()
    return CleanTimer(id = id, enabled = enabled, hour = hour, minute = minute, weekdays = weekdays)
}

const val CONSUMABLE_UNSUPPORTED = "unsupported"


internal fun VacuumStatus.merge(fresh: VacuumStatus): VacuumStatus = fresh.copy(
    battery = fresh.battery ?: battery,
    state = fresh.state ?: state,
    fanPower = fresh.fanPower ?: fanPower,
    cleanArea = fresh.cleanArea ?: cleanArea,
    cleanTime = fresh.cleanTime ?: cleanTime,
    errorCode = fresh.errorCode ?: errorCode,
    waterBoxStatus = fresh.waterBoxStatus ?: waterBoxStatus,
    waterBoxCustomMode = fresh.waterBoxCustomMode ?: waterBoxCustomMode,
    chargeStatus = fresh.chargeStatus ?: chargeStatus,
    dockErrorStatus = fresh.dockErrorStatus ?: dockErrorStatus,
    dryStatus = fresh.dryStatus ?: dryStatus,
    remainingDryTimeSec = fresh.remainingDryTimeSec ?: remainingDryTimeSec,
)


internal fun VacuumStatus.applyDpsPush(push: Map<Int, JsonElement>): VacuumStatus {
    if (push.isEmpty()) return this
    val state = push[121]?.jsonPrimitive?.intOrNull ?: state
    val battery = push[122]?.jsonPrimitive?.intOrNull ?: battery
    val fanPower = push[123]?.jsonPrimitive?.intOrNull ?: fanPower
    val waterBoxMode = push[124]?.jsonPrimitive?.intOrNull ?: waterBoxCustomMode
    val errorCode = push[120]?.jsonPrimitive?.intOrNull ?: this.errorCode
    val chargeStatus = push[133]?.jsonPrimitive?.intOrNull ?: this.chargeStatus
    val cleanArea = push[128]?.jsonPrimitive?.longOrNull ?: this.cleanArea
    val dryStatus = push[134]?.jsonPrimitive?.intOrNull ?: this.dryStatus
    return copy(
        state = state,
        battery = battery,
        fanPower = fanPower,
        waterBoxCustomMode = waterBoxMode,
        errorCode = errorCode,
        chargeStatus = chargeStatus,
        cleanArea = cleanArea,
        dryStatus = dryStatus,
    )
}
