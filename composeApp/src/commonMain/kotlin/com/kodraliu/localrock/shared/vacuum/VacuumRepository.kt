package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.model.Room
import com.kodraliu.localrock.shared.mqtt.MqttClient
import com.kodraliu.localrock.shared.mqtt.MqttTopics
import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.protocol.saveDebugBlob
import com.kodraliu.localrock.shared.vacuum.map.FloorMaterial
import com.kodraliu.localrock.shared.vacuum.map.MapFormat
import com.kodraliu.localrock.shared.vacuum.map.MapZone
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom
import com.kodraliu.localrock.shared.vacuum.map.VirtualWall
import com.kodraliu.localrock.shared.vacuum.map.parseB01Map
import com.kodraliu.localrock.shared.vacuum.map.parseLegacyMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val rriotKeyProvider: () -> String,
    private val mqttClient: MqttClient,
    topicsProvider: () -> MqttTopics,
    private val nowEpochSeconds: () -> Int,
    private val pollIntervalMs: Long = 5_000L,
    private val demo: Boolean = false,
) {
    val session: VacuumSession = VacuumSession(
        localKey = localKey,
        mqtt = mqttClient,
        topicsProvider = topicsProvider,
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

    /**
     * The robot's segment table exactly as `get_room_mapping` returned it: segment, room id (kept
     * as the raw string) and tag. `name_segment` must be sent the whole table, so entries this app
     * does not change are written back unchanged.
     */
    private var rawSegmentTable: List<SegmentEntry> = emptyList()

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

    /**
     * True once [floorMaps] holds a real answer from the robot. An empty list alone can't tell
     * "not loaded yet" from "no saved map", and map edits depend on the difference.
     */
    private val _floorMapsLoaded = MutableStateFlow(false)
    val floorMapsLoaded: StateFlow<Boolean> = _floorMapsLoaded

    /** Largest number of saved maps the robot allows (max_multi_map), once the list is read. */
    private val _maxFloorMaps = MutableStateFlow<Int?>(null)
    val maxFloorMaps: StateFlow<Int?> = _maxFloorMaps

    /** Last map this app loaded; only used until the robot's status names the loaded map. */
    private val _currentFloorFlag = MutableStateFlow(0)

    private val _mopMode = MutableStateFlow<Int?>(null)
    val mopMode: StateFlow<Int?> = _mopMode

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The loaded map's mapFlag, from the robot's map_status when it reports one. */
    val currentFloorFlag: StateFlow<Int> = combine(_status, _currentFloorFlag) { status, loadedHere ->
        status.loadedMapFlag?.takeIf { it != UNSAVED_MAP_FLAG } ?: loadedHere
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** True while the robot holds a newly built map that has not been saved yet. */
    val unsavedMapPresent: StateFlow<Boolean> = _status.map { it.loadedMapFlag == UNSAVED_MAP_FLAG }
        .stateIn(scope, SharingStarted.Eagerly, false)
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
                runCatching { session.ensureSubscribed() }
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
                            // A timed-out RPC is not proof of a dead socket: a slow response or a
                            // big map transfer causes the same symptom, and reconnecting then
                            // drops every in-flight request and makes it worse. Only tear down
                            // when the link is reported down or has gone completely silent.
                            val silentMs = mqttClient.millisSinceLastMessage()
                            val linkDead = !mqttClient.connected.value || silentMs >= STALE_LINK_SILENCE_MS
                            if (linkDead) {
                                println("[VacLocal] $consecutiveFailures poll failures, link silent ${silentMs}ms — forcing MQTT reconnect")
                                runCatching { mqttClient.forceReconnect() }
                            } else {
                                println("[VacLocal] $consecutiveFailures poll failures but link is alive (${silentMs}ms since last message) — not reconnecting")
                            }
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
            rawSegmentTable = result.mapNotNull { entry ->
                if (entry is JsonArray && entry.size >= 3) {
                    val segment = entry[0].jsonPrimitive.intOrNull ?: return@mapNotNull null
                    val tag = entry[2].jsonPrimitive.intOrNull ?: return@mapNotNull null
                    SegmentEntry(segment, entry[1].jsonPrimitive.content, tag)
                } else null
            }
            // Names resolve from the first two columns, which every firmware returns.
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

    private val mapEditMutex = Mutex()

    /** Why the map can't be edited right now, or null when it can. */
    fun editBlockedReason(map: ParsedMap? = _parsedMap.value, state: Int? = _status.value.state): String? = when {
        demo -> "Map editing is not available in demo mode"
        state in ACTIVE_STATES -> "The robot is busy. Dock it or let it finish before editing the map."
        map == null -> "The map has not loaded yet"
        map.format != MapFormat.LEGACY -> "Map editing is not supported for this robot's map format yet"
        else -> null
    }

    /** Current map, checked to be one this app can edit safely. */
    private fun editableMap(): ParsedMap {
        editBlockedReason()?.let { throw MapEditException(it) }
        return _parsedMap.value ?: throw MapEditException("The map has not loaded yet")
    }

    /**
     * Fetch the map until [check] passes or the attempts run out. The robot rewrites its map after
     * an edit, so the first map after the command can still be the old one.
     */
    private suspend fun awaitMap(check: (ParsedMap) -> Boolean): ParsedMap? {
        repeat(READBACK_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(READBACK_DELAY_MS)
            runCatching { fetchMap() }
            val map = _parsedMap.value
            if (map != null && check(map)) return map
        }
        return null
    }

    /**
     * Write the edited no-go zones, walls, carpets and thresholds. Each category is its own
     * full-set command (save_map, set_carpet_area, app_set_smart_door_sill), sent only when that
     * category changed: whatever a command leaves out is deleted. save_map is refused when the map
     * holds restrictions this app can't write back ([restrictionSaveBlocker]). Succeeds only when a
     * fresh map shows every category as sent.
     */
    suspend fun saveMapEdits(
        noGoZones: List<MapZone>,
        walls: List<VirtualWall>,
        carpets: List<MapZone>,
        thresholds: List<MapZone>,
    ): Unit = mapEditMutex.withLock {
        val map = editableMap()
        runCatching { fetchFloorMaps() }
        if (!_floorMapsLoaded.value) {
            throw MapEditException("Couldn't read the robot's saved maps, so nothing was saved. Try again.")
        }
        val mapIndex = saveMapIndex(_floorMaps.value)
        mapIndexBlocker(mapIndex)?.let { throw MapEditException(it) }
        val index = mapIndex!!
        val restrictionsChanged = !restrictionsMatch(map, noGoZones, map.noMopZones, walls)
        val carpetsChanged = !sameZones(carpets, map.carpetAreas)
        val thresholdsChanged = !sameZones(thresholds, map.thresholds)
        if (!restrictionsChanged && !carpetsChanged && !thresholdsChanged) return@withLock
        if (restrictionsChanged) restrictionSaveBlocker(map, mapIndex)?.let { throw MapEditException(it) }
        session.inEditSession {
            if (restrictionsChanged) session.saveMap(noGoZones, walls, index)
            if (carpetsChanged) session.setCarpetAreas(carpets, index, map.carpetFlagsPresent)
            if (thresholdsChanged) session.setThresholds(thresholds, index)
        }
        awaitMap { m ->
            restrictionsMatch(m, noGoZones, map.noMopZones, walls) &&
                sameZones(carpets, m.carpetAreas) &&
                m.carpetAreas.count { it.isRoundCarpet } == carpets.count { it.isRoundCarpet } &&
                sameZones(thresholds, m.thresholds)
        } ?: throw MapEditException(NOT_CONFIRMED)
    }

    /**
     * Split a room along the line A-B (robot mm). The robot is picky about which end comes first
     * (see [splitLineAttempts]), so a refused line is tried once more reversed. A refused split
     * changes nothing. The map must gain a room.
     */
    suspend fun splitRoom(segment: Int, xA: Int, yA: Int, xB: Int, yB: Int): Unit = mapEditMutex.withLock {
        val map = editableMap()
        val before = map.rooms.map { it.id }.toSet()
        if (segment !in before) throw MapEditException("That room is not on the current map")
        val attempts = splitLineAttempts(xA to yA, xB to yB)
        // The official app sent split_segment without start/end_edit_map.
        for ((i, line) in attempts.withIndex()) {
            val (a, b) = line
            try {
                session.splitSegment(segment, a.first, a.second, b.first, b.second)
                break
            } catch (e: RobotCommandException) {
                if (e.code() != SPLIT_FAILED_CODE || i == attempts.lastIndex) throw e
                // After refusing a split the robot sends a second, late error for the same
                // request (~10 s later in the capture). Only try again once that has passed.
                delay(SPLIT_RETRY_DELAY_MS)
            }
        }
        awaitMap { m -> (m.rooms.map { it.id }.toSet() - before).isNotEmpty() }
            ?: throw MapEditException(NOT_CONFIRMED)
        runCatching { rooms() }
    }

    /** Merge two neighbouring rooms. The map must lose one of the two ids. */
    suspend fun mergeRooms(segmentA: Int, segmentB: Int): Unit = mapEditMutex.withLock {
        val map = editableMap()
        val before = map.rooms.map { it.id }.toSet()
        if (segmentA == segmentB || segmentA !in before || segmentB !in before) {
            throw MapEditException("Pick two different rooms on the current map")
        }
        // Captured without start/end_edit_map, like split.
        session.mergeSegment(segmentA, segmentB)
        awaitMap { m -> m.rooms.map { it.id }.toSet().let { segmentA !in it || segmentB !in it } }
            ?: throw MapEditException(NOT_CONFIRMED)
        runCatching { rooms() }
    }

    /**
     * Set a room's floor type. Returns false when the command succeeded but this robot's map does
     * not report floor types, so the change could not be confirmed.
     */
    suspend fun setFloorMaterial(segment: Int, material: FloorMaterial, direction: Int?): Boolean =
        mapEditMutex.withLock {
            val map = editableMap()
            if (map.rooms.none { it.id == segment }) throw MapEditException("That room is not on the current map")
            if (direction != null && material != FloorMaterial.WOOD) {
                throw MapEditException("Only wood floors have a direction")
            }
            session.inEditSession { session.setSegmentGroundMaterial(segment, material.id, direction) }
            if (map.segmentMaterials.isEmpty()) {
                runCatching { fetchMap() }
                return@withLock false
            }
            awaitMap { m ->
                m.segmentMaterials[segment] == material.id &&
                    (direction == null || m.segmentMaterialDirections[segment] == direction)
            } ?: throw MapEditException(NOT_CONFIRMED)
            true
        }

    /**
     * Point [segment] at cloud room [roomId] with name_segment, sending the robot's full table with
     * only this entry changed. The robot may renumber segments in its answer; the check follows
     * that. Confirmed by reading the table back.
     */
    suspend fun assignRoom(segment: Int, roomId: Long): Unit = mapEditMutex.withLock {
        val map = editableMap()
        if (map.rooms.none { it.id == segment }) throw MapEditException("That room is not on the current map")
        rooms()
        val table = rawSegmentTable
        if (table.isEmpty()) throw MapEditException("The robot did not return its room table. Nothing was changed.")
        val existing = table.find { it.segment == segment }
        val entry = SegmentEntry(segment, roomId.toString(), existing?.tagId ?: NEW_SEGMENT_TAG)
        val resp = session.nameSegment(table.filter { it.segment != segment } + entry)
        val finalSegment = parseSegmentRenumbering(resp.result)[segment] ?: segment
        rooms()
        if (rawSegmentTable.find { it.segment == finalSegment }?.roomId != roomId.toString()) {
            throw MapEditException(NOT_CONFIRMED)
        }
    }

    /** Why the robot's saved maps can't be renamed, deleted or rebuilt right now, or null. */
    fun mapManageBlockedReason(state: Int? = _status.value.state): String? = when {
        demo -> "Managing maps is not available in demo mode"
        state in ACTIVE_STATES -> "The robot is busy. Dock it or let it finish first."
        else -> null
    }

    /** A fresh map list that holds [mapFlag], or an error saying why not. */
    private suspend fun requireFloorMap(mapFlag: Int): FloorMap {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        runCatching { fetchFloorMaps() }
        if (!_floorMapsLoaded.value) throw MapEditException("Couldn't read the robot's saved maps. Nothing was changed.")
        return _floorMaps.value.find { it.mapFlag == mapFlag }
            ?: throw MapEditException("That map is no longer on the robot")
    }

    /** Rename a saved map. Confirmed by reading the map list back. */
    suspend fun renameFloorMap(mapFlag: Int, name: String): Unit = mapEditMutex.withLock {
        val clean = name.trim()
        mapNameBlocker(clean)?.let { throw MapEditException(it) }
        requireFloorMap(mapFlag)
        session.nameMultiMap(mapFlag, clean)
        fetchFloorMaps()
        val saved = _floorMaps.value.find { it.mapFlag == mapFlag }?.name
        if (saved != clean) throw MapEditException(NOT_CONFIRMED)
    }

    /**
     * Delete a saved map. The robot forgets its rooms and everything drawn on it; room names stay
     * on the server but no longer point anywhere. Confirmed by the map leaving the list.
     */
    suspend fun deleteFloorMap(mapFlag: Int): Unit = mapEditMutex.withLock {
        requireFloorMap(mapFlag)
        session.deleteMap(mapFlag)
        fetchFloorMaps()
        if (_floorMaps.value.any { it.mapFlag == mapFlag }) throw MapEditException(NOT_CONFIRMED)
        _parsedMap.value = null
        _mapBytes.value = null
        _rooms.value = emptyList()
        rawSegmentTable = emptyList()
    }

    /**
     * Send the robot out to map the home from its dock, while the robot has room for another map
     * (captured with none left on a one-map robot, and with 2 of 4). The new map is kept only
     * once [saveNewMap] stores it.
     */
    suspend fun startMapping(): Unit = mapEditMutex.withLock {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        runCatching { fetchFloorMaps() }
        if (!_floorMapsLoaded.value) throw MapEditException("Couldn't read the robot's saved maps. Nothing was started.")
        val max = _maxFloorMaps.value ?: 1
        if (_floorMaps.value.size >= max) {
            throw MapEditException(
                if (max <= 1) "The robot still has a saved map. Delete it first to map the home again."
                else "The robot already holds $max maps, its limit. Delete one first."
            )
        }
        session.startBuildMap()
    }

    /**
     * Turn multi-level maps on or off. Turning them off keeps only [keepMapFlag] and the robot
     * deletes every other saved map (captured). Confirmed by status and the map list.
     */
    suspend fun setMultiLevel(enabled: Boolean, keepMapFlag: Int?): Unit = mapEditMutex.withLock {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        runCatching { refreshStatus() }
        val lab = _status.value.labStatus
            ?: throw MapEditException("The robot didn't report its map settings. Nothing was changed.")
        if (enabled) {
            session.setLabStatus(lab or LAB_STATUS_MULTI_LEVEL, reserveMap = null)
            // The official app followed this with manual map selection.
            session.setSwitchMapMode(SWITCH_MAP_MODE_MANUAL)
        } else {
            runCatching { fetchFloorMaps() }
            if (!_floorMapsLoaded.value) throw MapEditException("Couldn't read the robot's saved maps. Nothing was changed.")
            val keep = keepMapFlag ?: _floorMaps.value.singleOrNull()?.mapFlag
                ?: throw MapEditException("Pick the map to keep")
            if (_floorMaps.value.none { it.mapFlag == keep }) throw MapEditException("That map is no longer on the robot")
            session.setLabStatus(lab and LAB_STATUS_MULTI_LEVEL.inv(), reserveMap = keep)
        }
        if (!awaitStatus { it.multiLevelEnabled == enabled }) throw MapEditException(NOT_CONFIRMED)
        runCatching { fetchFloorMaps() }
        if (!enabled) {
            rawSegmentTable = emptyList()
            runCatching { fetchMap() }
            runCatching { rooms() }
        }
    }

    /** Recognise the floor automatically, or pick the map by hand. Confirmed by status. */
    suspend fun setSmartMapSwitching(smart: Boolean): Unit = mapEditMutex.withLock {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        val mode = if (smart) SWITCH_MAP_MODE_SMART else SWITCH_MAP_MODE_MANUAL
        session.setSwitchMapMode(mode)
        if (!awaitStatus { it.switchMapMode == mode }) throw MapEditException(NOT_CONFIRMED)
    }

    /** Status caught up 1-2 s after the robot's "ok" in the capture, so poll briefly. */
    private suspend fun awaitStatus(check: (VacuumStatus) -> Boolean): Boolean {
        repeat(STATUS_READBACK_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(STATUS_READBACK_DELAY_MS)
            runCatching { refreshStatus() }
            if (check(_status.value)) return true
        }
        return false
    }

    /**
     * Save the map the robot just built as a new saved map, as the official app does after a
     * mapping run is stopped. Confirmed by a new entry in the map list.
     */
    suspend fun saveNewMap(): Unit = mapEditMutex.withLock {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        if (!unsavedMapPresent.value) throw MapEditException("There is no new map to save")
        runCatching { fetchFloorMaps() }
        val before = _floorMaps.value.map { it.mapFlag }.toSet()
        session.saveNewMap()
        fetchFloorMaps()
        val added = _floorMaps.value.map { it.mapFlag }.toSet() - before
        if (added.isEmpty()) throw MapEditException(NOT_CONFIRMED)
        runCatching { refreshStatus() }
        rawSegmentTable = emptyList()
        runCatching { fetchMap() }
        runCatching { rooms() }
    }

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


    /** Loads the robot's saved-map list into [floorMaps]. */
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
        _maxFloorMaps.value = obj["max_multi_map"]?.jsonPrimitive?.intOrNull
        _floorMaps.value = mapInfoArr.mapNotNull { elem ->
            val o = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val flag = o["mapFlag"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            // An unnamed map must still be listed: saving zones relies on the count being right.
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Map ${flag + 1}"
            FloorMap(name, flag)
        }
        _floorMapsLoaded.value = true
    }

    suspend fun switchFloor(mapFlag: Int) {
        mapManageBlockedReason()?.let { throw MapEditException(it) }
        session.loadMultiMap(mapFlag)
        _currentFloorFlag.value = mapFlag
        runCatching { refreshStatus() }
        rawSegmentTable = emptyList()
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

    private val mapFetchMutex = Mutex()

    /**
     * Serialised because the poll loop and the user's refresh button both call this, and
     * [VacuumSession] tracks a single outstanding map request — two at once orphaned the first.
     */
    suspend fun fetchMap(): MapResponse = mapFetchMutex.withLock { fetchMapLocked() }

    private suspend fun fetchMapLocked(): MapResponse {
        if (demo) {
            _mapStatus.value = "Demo mode — live map unavailable"
            return MapResponse(requestId = 0, data = ByteArray(0))
        }
        _mapStatus.value = "requesting…"
        val sd = createSecurityData(rriotKeyProvider())
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
        const val MAP_FAST_POLL_MS = 8_000L
        const val MAP_IDLE_POLL_MS = 60_000L
        const val MAX_HISTORY_RECORDS = 20

        const val READBACK_ATTEMPTS = 4
        const val READBACK_DELAY_MS = 2_000L
        const val SPLIT_RETRY_DELAY_MS = 12_000L
        /**
         * Tag for a segment that gets its first table entry. In the capture the official app gave
         * the previously unlisted segment 24 tag 12 when the user named it.
         */
        const val NEW_SEGMENT_TAG = 12
        const val STATUS_READBACK_ATTEMPTS = 6
        const val STATUS_READBACK_DELAY_MS = 1_000L

        const val NOT_CONFIRMED =
            "The robot accepted the change, but the map does not show it yet. Refresh the map in a moment to check."

        const val STALE_LINK_FAILURE_THRESHOLD = 3

        /** Silence that, together with failing polls, means the link really is gone. */
        const val STALE_LINK_SILENCE_MS = 30_000L
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
            VacuumStateCodes.MAPPING,
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
    mapStatus = fresh.mapStatus ?: mapStatus,
    labStatus = fresh.labStatus ?: labStatus,
    switchMapMode = fresh.switchMapMode ?: switchMapMode,
)


internal fun VacuumStatus.applyDpsPush(push: Map<Int, JsonElement>): VacuumStatus {
    if (push.isEmpty()) return this
    val state = push[121]?.jsonPrimitive?.intOrNull ?: state
    val battery = push[122]?.jsonPrimitive?.intOrNull ?: battery
    val fanPower = push[123]?.jsonPrimitive?.intOrNull ?: fanPower
    val waterBoxMode = push[124]?.jsonPrimitive?.intOrNull ?: waterBoxCustomMode
    val errorCode = push[120]?.jsonPrimitive?.intOrNull ?: this.errorCode
    val chargeStatus = push[133]?.jsonPrimitive?.intOrNull ?: this.chargeStatus
    val dryStatus = push[134]?.jsonPrimitive?.intOrNull ?: this.dryStatus
    return copy(
        state = state,
        battery = battery,
        fanPower = fanPower,
        waterBoxCustomMode = waterBoxMode,
        errorCode = errorCode,
        chargeStatus = chargeStatus,
        dryStatus = dryStatus,
    )
}
