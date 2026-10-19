package com.kodraliu.localrock.ui.vacuum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.vacuum.MapEditException
import com.kodraliu.localrock.shared.vacuum.RoomType
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
import com.kodraliu.localrock.shared.vacuum.isSmartPlanSupported
import com.kodraliu.localrock.shared.vacuum.isWaterSlideSupported
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val supportsWaterSlide: Boolean = isWaterSlideSupported(device.newFeatureSet)
    val repository: VacuumRepository = container.vacuumRepositoryFor(device)
    val status = repository.status

    private val errorBannerKey = "robot-error-${device.duid}"

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _firmwareUpdate = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val firmwareUpdate: StateFlow<FirmwareUpdateState> = _firmwareUpdate.asStateFlow()


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
            _busy.value = true
            try {
                container.ensureMqttConnected()
                repository.attach()
            } catch (e: Throwable) {
                container.messages.error(describe(e))
            } finally {
                _busy.value = false
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
            _busy.value = true
            try {
                repository.fetchMap()
                runCatching { repository.rooms() }
            } catch (e: Throwable) {
                container.messages.error(describe(e))
                runCatching { repository.forceReconnect() }
            } finally {
                _busy.value = false
            }
        }
    }


    /**
     * Give a room a custom name the way the Roborock app does: the name becomes a cloud room and the
     * segment points at it, keeping the room's type tag so the robot still treats it as that type.
     */
    suspend fun renameRoom(segment: Int, name: String) {
        val room = container.deviceRepository.createRoom(validateRoomName(name))
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
        val room = container.deviceRepository.createRoom(type.label)
        repository.assignRoom(segment, room.id, type.tag)
        container.deviceRepository.refresh()
        repository.setHomeRooms(container.deviceRepository.home.value?.rooms ?: emptyList())
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

    fun run(successMessage: String? = null, action: suspend (VacuumRepository) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                action(repository)

                runCatching { repository.refreshStatus() }
                successMessage?.let { container.messages.success(it) }
            } catch (e: Throwable) {
                container.messages.error(describe(e))
            } finally {
                _busy.value = false
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
