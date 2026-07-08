package com.kodraliu.localrock.shared.auth

import com.kodraliu.localrock.shared.demo.DemoData
import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.model.UserData
import com.kodraliu.localrock.shared.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val settings: AppSettings,
    private val loginApi: LoginApi,
) {

    private val _userData = MutableStateFlow(loadFromDisk())
    val userData: StateFlow<UserData?> = _userData.asStateFlow()

    private fun loadFromDisk(): UserData? {
        val json = settings.readUserDataJson() ?: return null
        return runCatching { ProdJson.decodeFromString<UserData>(json) }.getOrNull()
    }

    suspend fun login(email: String, code: String): UserData {
        val data = loginApi.login(email, code)
        settings.writeUserDataJson(ProdJson.encodeToString(data))
        _userData.value = data
        return data
    }

    /**
     * Enter offline demo mode: seed a fake session and flip the demo flag so the repositories
     * serve fabricated data instead of hitting the network. Used by app-store reviewers.
     */
    fun enterDemo() {
        val data = DemoData.userData
        settings.writeUserDataJson(ProdJson.encodeToString(data))
        settings.setDemoMode(true)
        _userData.value = data
    }

    fun logout() {
        settings.writeUserDataJson(null)
        settings.setDemoMode(false)
        _userData.value = null
    }

    fun hawkCreds(): HawkCreds? {
        val rriot = _userData.value?.rriot ?: return null
        return HawkCreds(id = rriot.u, session = rriot.s, key = rriot.h)
    }

    fun token(): String? = _userData.value?.token
}
