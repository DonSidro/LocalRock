package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.auth.AuthRepository
import com.kodraliu.localrock.shared.demo.DemoData
import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.model.FirmwareUpdateInfo
import com.kodraliu.localrock.shared.model.Home
import com.kodraliu.localrock.shared.model.Product
import com.kodraliu.localrock.shared.model.Room
import com.kodraliu.localrock.shared.model.Scene
import com.kodraliu.localrock.shared.model.SceneRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceRepository(
    private val deviceApi: DeviceApi,
    private val authRepository: AuthRepository,
    private val isDemo: () -> Boolean = { false },
    /** Asks one robot whether it is there: true on a reply, false on silence, throws if it could not ask. */
    private val probe: suspend (Device) -> Boolean = { it.online },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _home = MutableStateFlow<Home?>(null)
    val home: StateFlow<Home?> = _home.asStateFlow()

    val devices: StateFlow<List<Device>> get() = _devicesFlow

    private val _devicesFlow = MutableStateFlow<List<Device>>(emptyList())

    private val _products = MutableStateFlow<Map<String, Product>>(emptyMap())
    val productsByDuid: StateFlow<Map<String, Product>> = _products.asStateFlow()

    /** Per duid; a robot with no entry has not been asked, so its server `online` flag is all there is. */
    private val _reachability = MutableStateFlow<Map<String, Reachability>>(emptyMap())
    val reachability: StateFlow<Map<String, Reachability>> = _reachability.asStateFlow()

    private var probeJob: Job? = null

    /**
     * Ask every paired robot directly whether it is reachable. Returns at once; verdicts land in
     * [reachability] as they come in. A robot keeps its previous verdict while it is re-checked,
     * so a refresh does not flicker every card through "Checking".
     */
    fun probeReachability() {
        if (isDemo()) return
        val targets = _devicesFlow.value.filter { it.localKey != null }
        probeJob?.cancel()
        _reachability.update { previous ->
            targets.associate { device ->
                val known = previous[device.duid]?.takeIf { it != Reachability.CHECKING }
                device.duid to (known ?: Reachability.CHECKING)
            }
        }
        probeJob = scope.launch {
            for (device in targets) launch {
                val verdict = try {
                    if (probe(device)) Reachability.ONLINE else Reachability.OFFLINE
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // Could not ask at all (no MQTT link), which says nothing about the robot.
                    println("[VacLocal] reachability probe for ${device.duid} could not run: ${e.message}")
                    null
                }
                _reachability.update { if (verdict == null) it - device.duid else it + (device.duid to verdict) }
            }
        }
    }

    suspend fun refresh() {
        if (isDemo()) {
            val home = DemoData.home
            _home.value = home
            _devicesFlow.value = home.devices
            val productsByDeviceId = home.products.associateBy { it.id }
            _products.value = home.devices.associate { it.duid to productsByDeviceId[it.productId]!! }
            return
        }
        val token = authRepository.token() ?: error("Not logged in")
        // NOTE: do NOT call /nc/prepare here. It's an onboarding-only ("network config prepare")
        // endpoint; calling it on every refresh made local_roborock_server fabricate a phantom
        // "rr_<id>" runtime-credential device (local_key_source=onboarding_nc) that then showed up
        // as a ghost vacuum in the device list. The result was discarded anyway.
        val detail = deviceApi.getHomeDetail(token)
        val homeId = detail.resolvedHomeId ?: error("Server did not return a home id")
        val home = deviceApi.getHome(homeId)
        _home.value = home
        _devicesFlow.value = home.devices
        val productsByDeviceId = home.products.associateBy { it.id }
        _products.value = home.devices.associate { it.duid to productsByDeviceId[it.productId]!! }
    }

    /** Create (or look up by name) a cloud room in the current home. */
    suspend fun createRoom(name: String): Room {
        if (isDemo()) error("Rooms can't be renamed in demo mode")
        val homeId = _home.value?.id ?: error("Home not loaded")
        return deviceApi.createRoom(homeId, name)
    }

    /** Routines saved for [duid] (made in the official app; local_roborock_server keeps them). */
    suspend fun scenes(duid: String): List<Scene> {
        if (isDemo()) return emptyList()
        return deviceApi.getScenes(duid)
    }

    suspend fun runScene(sceneId: Long): SceneRunResult {
        if (isDemo()) error("Routines can't be run in demo mode")
        return deviceApi.executeScene(sceneId)
    }

    suspend fun checkFirmwareUpdate(duid: String): FirmwareUpdateInfo? {
        if (isDemo()) {
            val fv = _devicesFlow.value.find { it.duid == duid }?.fv
            return FirmwareUpdateInfo(currentVersion = fv, version = fv, updatable = false)
        }
        return deviceApi.checkFirmwareUpdate(duid)
    }
}
