package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.auth.AuthRepository
import com.kodraliu.localrock.shared.demo.DemoData
import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.model.FirmwareUpdateInfo
import com.kodraliu.localrock.shared.model.Home
import com.kodraliu.localrock.shared.model.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceRepository(
    private val deviceApi: DeviceApi,
    private val authRepository: AuthRepository,
    private val isDemo: () -> Boolean = { false },
) {

    private val _home = MutableStateFlow<Home?>(null)
    val home: StateFlow<Home?> = _home.asStateFlow()

    val devices: StateFlow<List<Device>> get() = _devicesFlow

    private val _devicesFlow = MutableStateFlow<List<Device>>(emptyList())

    private val _products = MutableStateFlow<Map<String, Product>>(emptyMap())
    val productsByDuid: StateFlow<Map<String, Product>> = _products.asStateFlow()

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
        runCatching { deviceApi.ncPrepare() }
        val detail = deviceApi.getHomeDetail(token)
        val homeId = detail.resolvedHomeId ?: error("Server did not return a home id")
        val home = deviceApi.getHome(homeId)
        _home.value = home
        _devicesFlow.value = home.devices
        val productsByDeviceId = home.products.associateBy { it.id }
        _products.value = home.devices.associate { it.duid to productsByDeviceId[it.productId]!! }
    }

    suspend fun checkFirmwareUpdate(duid: String): FirmwareUpdateInfo? {
        if (isDemo()) {
            val fv = _devicesFlow.value.find { it.duid == duid }?.fv
            return FirmwareUpdateInfo(currentVersion = fv, version = fv, updatable = false)
        }
        return deviceApi.checkFirmwareUpdate(duid)
    }
}
