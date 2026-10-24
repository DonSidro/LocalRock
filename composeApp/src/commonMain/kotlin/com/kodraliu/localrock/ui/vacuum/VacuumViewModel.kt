package com.kodraliu.localrock.ui.vacuum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.model.SceneRunResult
import com.kodraliu.localrock.shared.vacuum.DockCapabilities
import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.RoomType
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.isFastRouteSupported
import com.kodraliu.localrock.shared.vacuum.isSmartPlanSupported
import com.kodraliu.localrock.shared.vacuum.isWaterSlideSupported
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val firmwareVersion: String? = device.fv
    val supportsSmartPlan: Boolean = isSmartPlanSupported(device.newFeatureSet)
    val supportsFastRoute: Boolean = isFastRouteSupported(device.newFeatureSet)
    val supportsWaterSlide: Boolean = isWaterSlideSupported(device.newFeatureSet)
    val dockCapabilities = DockCapabilities(modelName, device.featureSet, device.newFeatureSet)
    val repository: VacuumRepository = container.vacuumRepositoryFor(device)
    val status = repository.status

    private val errorBannerKey = "robot-error-${device.duid}"

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val actionMutex = Mutex()
    private var pendingActions = 0

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
