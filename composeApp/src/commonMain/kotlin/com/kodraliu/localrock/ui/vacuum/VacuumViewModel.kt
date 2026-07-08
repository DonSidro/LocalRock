package com.kodraliu.localrock.ui.vacuum

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import com.kodraliu.localrock.shared.messages.MessageSeverity
import com.kodraliu.localrock.shared.vacuum.VacuumErrorCodes
import com.kodraliu.localrock.shared.vacuum.VacuumRepository
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
                runCatching { repository.fetchConsumableStatus() }
            } catch (e: Throwable) {
                container.messages.error(describe(e))
            } finally {
                _busy.value = false
            }
            launch { runCatching { repository.rooms() } }
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

private fun describe(e: Throwable): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = e
    while (current != null) {
        val cls = current::class.simpleName ?: "Throwable"
        val msg = current.message
        parts += if (msg.isNullOrBlank()) cls else "$cls: $msg"
        current = current.cause?.takeIf { it !== current }
    }
    return parts.joinToString(" → ")
}
