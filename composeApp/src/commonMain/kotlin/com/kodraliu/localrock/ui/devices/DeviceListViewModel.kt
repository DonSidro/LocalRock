package com.kodraliu.localrock.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kodraliu.localrock.shared.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceListViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val devices = container.deviceRepository.devices
    val productsByDuid = container.deviceRepository.productsByDuid

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                container.deviceRepository.refresh()
            } catch (e: Throwable) {
                _error.value = e.message ?: "Failed to load devices"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun logout() = container.authRepository.logout()
}
