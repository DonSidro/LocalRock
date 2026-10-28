package com.kodraliu.localrock.ui.vacuum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.model.SceneRunResult
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.FeatureSection
import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.RoomType
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.ProductCatalog
import com.kodraliu.localrock.shared.vacuum.isSmartPlanSupported
import com.kodraliu.localrock.shared.vacuum.isWaterSlideSupported
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kodraliu.localrock.shared.vacuum.supportedBetaFeatures
import com.kodraliu.localrock.shared.vacuum.unlistedAdvertisedFeatures
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VacuumViewModel(
    duid: String,
    private val container: AppContainer,
) : ViewModel() {

    private val device = requireNotNull(container.deviceRepository.devices.value.find { it.duid == duid }) {
        "Device $duid not found — navigate to DeviceList first to load devices"
    }

    val deviceName: String = device.name
    val modelName: String? = container.deviceRepository.productsByDuid.value[duid]?.model
    val modelDisplayName: String? = ProductCatalog.displayName(modelName)
    val firmwareVersion: String? = device.fv
    private val serverFeatureSet = device.newFeatureSet
    val dockCapabilities = DockCapabilities(modelName, device.featureSet, device.newFeatureSet)
    val betaFeatureDefs = supportedBetaFeatures(device.newFeatureSet)
    val unlistedFeatures = unlistedAdvertisedFeatures(device.newFeatureSet)
    val showsBetaFeatures = betaFeatureDefs.any { it.section == FeatureSection.BETA && it.bit != null } || unlistedFeatures.isNotEmpty()
    val repository: VacuumRepository = container.vacuumRepositoryFor(device)

    // The robot's own answer wins; the server's record is the fallback until it has replied.
    val supportsSmartPlan: StateFlow<Boolean> = repository.robotFeatureSet
        .map { isSmartPlanSupported(it ?: serverFeatureSet) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, isSmartPlanSupported(serverFeatureSet))
    val supportsWaterSlide: StateFlow<Boolean> = repository.robotFeatureSet
        .map { isWaterSlideSupported(it ?: serverFeatureSet) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, isWaterSlideSupported(serverFeatureSet))
    val status = repository.status

    private val errorBannerKey = "robot-error-${device.duid}"

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val actionMutex = Mutex()
    private var pendingActions = 0

    // The map's rotation and 2D/3D view are a purely local rendering choice: the robot has no
    // concept of either, so these live in this device's app settings rather than any RPC.
    private val _mapRotation = MutableStateFlow(container.appSettings.mapRotation(device.duid))
    val mapRotation: StateFlow<Int> = _mapRotation.asStateFlow()

    /** The flat 2D map's orientation. The 3D view has its own heading, [map3DYaw]. */
    fun rotateMap90() {
        val next = (_mapRotation.value + 90) % 360
        container.appSettings.setMapRotation(device.duid, next)
        _mapRotation.value = next
    }

    private val _map3DYaw = MutableStateFlow(container.appSettings.map3DYaw(device.duid))
    val map3DYaw: StateFlow<Float> = _map3DYaw.asStateFlow()
    private val _map3DTilt = MutableStateFlow(container.appSettings.map3DTilt(device.duid))
    val map3DTilt: StateFlow<Float> = _map3DTilt.asStateFlow()
    private var map3DSaveJob: Job? = null

    /** Turning the 3D model fires on every frame of a twist; the view updates at once. */
    fun setMap3DYaw(degrees: Float) {
        _map3DYaw.value = ((degrees % 360f) + 360f) % 360f
        saveMap3DViewSoon()
    }

    /** A twist or drag arrives as a change, so two events in one frame both count. */
    fun turnMap3DBy(degrees: Float) = setMap3DYaw(_map3DYaw.value + degrees)

    fun tiltMap3DBy(degrees: Float) = setMap3DTilt(_map3DTilt.value + degrees)

    fun setMap3DTilt(degrees: Float) {
        _map3DTilt.value = degrees.coerceIn(MAP_3D_MIN_TILT, MAP_3D_MAX_TILT)
        saveMap3DViewSoon()
    }

    // Only where the model settles is written to storage, not every frame of getting there.
    private fun saveMap3DViewSoon() {
        map3DSaveJob?.cancel()
        map3DSaveJob = viewModelScope.launch {
            delay(500)
            container.appSettings.setMap3DYaw(device.duid, _map3DYaw.value)
            container.appSettings.setMap3DTilt(device.duid, _map3DTilt.value)
        }
    }

    private val _map3D = MutableStateFlow(container.appSettings.map3D(device.duid))
    val map3D: StateFlow<Boolean> = _map3D.asStateFlow()

    fun setMap3D(value: Boolean) {
        container.appSettings.setMap3D(device.duid, value)
        _map3D.value = value
    }

    private val _firmwareUpdate = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val firmwareUpdate: StateFlow<FirmwareUpdateState> = _firmwareUpdate.asStateFlow()

    private val _routines = MutableStateFlow<RoutinesState>(RoutinesState.Loading)
    val routines: StateFlow<RoutinesState> = _routines.asStateFlow()


    fun checkFirmwareUpdate() {
        viewModelScope.launch {
            _firmwareUpdate.value = FirmwareUpdateState.Checking
            _firmwareUpdate.value = try {
                val info = container.deviceRepository.checkFirmwareUpdate(device.duid)
                when {
                    info == null -> FirmwareUpdateState.Unavailable("No firmware info returned")
                    info.updatable -> FirmwareUpdateState.Available(
                        current = info.currentVersion ?: firmwareVersion,
                        latest = info.version,
                        notes = info.desc,
                        force = info.forceUpdate,
                    )
                    else -> FirmwareUpdateState.UpToDate(info.currentVersion ?: firmwareVersion)
                }
            } catch (e: Throwable) {
                FirmwareUpdateState.Unavailable(describe(e))
            }
        }
    }

    init {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        container.ensureMqttConnected()
                        repository.attach()
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        container.messages.error(describe(e))
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
            // One-shot reads: the link may still be coming up, so wait for it rather than let
            // them fail and leave the consumables and room names empty.
            launch {
                container.awaitMqttConnected()
                runCatching { repository.fetchConsumableStatus() }
                runCatching { repository.rooms() }
                runCatching { repository.fetchRobotFeatureSet() }
            }
        }

        viewModelScope.launch {
            status.collect { s ->
                val code = s.errorCode ?: 0
                if (code != 0) {
                    container.messages.showBanner(
                        key = errorBannerKey,
                        text = "$deviceName: ${VacuumErrorCodes.describe(code)}",
                        severity = MessageSeverity.ERROR,
                    )
                } else {
                    container.messages.dismissBanner(errorBannerKey)
                }
            }
        }
    }

    override fun onCleared() {
        container.messages.dismissBanner(errorBannerKey)
        repository.detach()
    }

    fun refreshMap() {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        repository.fetchMap()
                        runCatching { repository.rooms() }
                        coroutineContext.ensureActive()
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        container.messages.error(describe(e))
                        runCatching { repository.forceReconnect() }
                        coroutineContext.ensureActive()
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
        }
    }


    /**
     * Give a room a custom name the way the Roborock app does: the name becomes a cloud room and the
     * segment points at it, keeping the room's type tag so the robot still treats it as that type.
     */
    suspend fun renameRoom(segment: Int, name: String) {
        val validName = validateRoomName(name)
        repository.checkRoomTable(segment)
        val room = container.deviceRepository.createRoom(validName)
        repository.assignRoom(segment, room.id)
        container.deviceRepository.refresh()
        repository.setHomeRooms(container.deviceRepository.home.value?.rooms ?: emptyList())
    }

    /**
     * Set a room's type the way the Roborock app does: get a cloud room for the type's label from
     * the server, point the robot's segment at it with `name_segment` along with the type's tag
     * (which the robot uses to adjust its cleaning), then reload the home so the name resolves.
     * Throws with a user-facing message on any failure.
     */
    suspend fun setRoomType(segment: Int, type: RoomType) {
        repository.checkRoomTable(segment)
        val room = container.deviceRepository.createRoom(type.label)
        repository.assignRoom(segment, room.id, type.tag)
        container.deviceRepository.refresh()
        repository.setHomeRooms(container.deviceRepository.home.value?.rooms ?: emptyList())
    }

    fun loadRoutines() {
        viewModelScope.launch {
            _routines.value = RoutinesState.Loading
            _routines.value = try {
                RoutinesState.Loaded(container.deviceRepository.scenes(device.duid))
            } catch (e: Throwable) {
                RoutinesState.Failed(describe(e))
            }
        }
    }

    /** The server runs the routine's steps itself; the robot's status shows how far it got. */
    fun runRoutine(scene: Scene) = run {
        val result = container.deviceRepository.runScene(scene.id)
        container.messages.success(routineRunMessage(scene.name, result))
    }

    /** The room order a full clean follows on the loaded map; empty when none has been set. */
    fun defaultCleanOrder(): List<Int> =
        container.appSettings.cleanOrder(device.duid, repository.currentFloorFlag.value)

    /** Kept only once the robot has taken the order, so the app never remembers one it refused. */
    fun saveDefaultCleanOrder(order: List<Int>) = run("Cleaning order saved") {
        it.saveCleanSequence(order)
        container.appSettings.setCleanOrder(device.duid, it.currentFloorFlag.value, order)
    }

    fun removeDefaultCleanOrder() {
        container.appSettings.setCleanOrder(device.duid, repository.currentFloorFlag.value, emptyList())
        container.messages.success("Default order removed")
    }

    /**
     * Runs a settings change and shows the error if the robot refuses it. Unlike [run] it neither
     * marks the screen busy nor reads the status back, which would be too heavy for a slider; the
     * settings are read again on a failure so the screen goes back to what the robot really holds.
     */
    fun report(action: suspend (VacuumRepository) -> Unit) {
        viewModelScope.launch {
            try {
                action(repository)
            } catch (e: Throwable) {
                container.messages.error(describe(e))
                runCatching { repository.fetchRobotSettings() }
            }
        }
    }

    fun run(successMessage: String? = null, action: suspend (VacuumRepository) -> Unit) {
        viewModelScope.launch {
            pendingActions++
            _busy.value = true
            try {
                actionMutex.withLock {
                    try {
                        action(repository)

                        runCatching { repository.refreshStatus() }
                        coroutineContext.ensureActive()
                        successMessage?.let { container.messages.success(it) }
                    } catch (e: Throwable) {
                        coroutineContext.ensureActive()
                        container.messages.error(describe(e))
                    }
                }
            } finally {
                pendingActions--
                _busy.value = pendingActions > 0
            }
        }
    }
}

sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState
    data object Checking : FirmwareUpdateState
    data class UpToDate(val current: String?) : FirmwareUpdateState
    data class Available(
        val current: String?,
        val latest: String?,
        val notes: String?,
        val force: Boolean,
    ) : FirmwareUpdateState
    data class Unavailable(val reason: String) : FirmwareUpdateState
}

sealed interface RoutinesState {
    data object Loading : RoutinesState
    data class Loaded(val routines: List<Scene>) : RoutinesState
    data class Failed(val reason: String) : RoutinesState
}

/** What to tell the user once the server has answered a run request; a refusal is thrown. */
internal fun routineRunMessage(name: String, result: SceneRunResult): String {
    if (!result.accepted) {
        val active = result.activeSceneName
        throw IllegalStateException(
            if (result.status == "routine_in_progress" && active != null) {
                "\"$active\" is still running. Run it again to stop it first."
            } else {
                "The server did not start \"$name\"" + (result.status?.let { " ($it)" } ?: "")
            }
        )
    }
    return if (result.status == "cancel_requested") "Stopping \"$name\"" else "\"$name\" started"
}

/**
 * Longest room name allowed. The name is stored by the server, not the robot, and its real limit
 * is unknown; 23 keeps names short enough for the room labels on the map.
 */
const val MAX_ROOM_NAME_LENGTH = 23

/**
 * Trimmed name, or [MapEditException] if unusable. A leading `{` or `[` makes the server treat
 * the form body as JSON and verify the request signature differently, so it is rejected here.
 */
fun validateRoomName(name: String): String {
    val clean = name.trim()
    if (clean.isEmpty()) throw MapEditException("Enter a room name")
    if (clean.length > MAX_ROOM_NAME_LENGTH) throw MapEditException("Room names can be at most $MAX_ROOM_NAME_LENGTH characters")
    if (clean.startsWith("{") || clean.startsWith("[")) throw MapEditException("Room names can't start with { or [")
    return clean
}

private fun describe(e: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = e
    while (current != null) {
        val msg = current.message
        parts += when {
            current is TimeoutCancellationException -> "The robot didn't answer in time"
            msg.isNullOrBlank() -> current::class.simpleName ?: "Throwable"
            else -> msg
        }
        current = current.cause?.takeIf { it !== current }
    }
    return parts.distinct().joinToString(" → ")
}
