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
        settings.writeLoginCredentials(email, code)
        _userData.value = data
        return data
    }

    /** True when [reLogin] has something to work with. */
    val canReLogin: Boolean get() = settings.readLoginCredentials() != null

    /**
     * Sign in again with the stored credentials to mint a fresh session. The server rejects MQTT
     * credentials once the session they belong to is evicted, and there is no token-refresh
     * endpoint, so replaying the login is the only way back.
     *
     * Returns null when there is nothing stored or the server refused; the caller is expected to
     * fall back to asking the user.
     */
    suspend fun reLogin(): UserData? {
        val (email, code) = settings.readLoginCredentials() ?: return null
        return try {
            login(email, code)
        } catch (e: LoginException) {
            // The stored credentials are no longer valid — drop them so we stop retrying.
            println("[VacLocal] silent re-login rejected (${e.responseCode}): ${e.message}")
            settings.writeLoginCredentials(null, null)
            null
        } catch (e: Throwable) {
            // Network trouble: keep the credentials, this is worth retrying later.
            println("[VacLocal] silent re-login failed: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Enter offline demo mode: seed a fake session and flip the demo flag so the repositories
     * serve fabricated data instead of hitting the network. Used by app-store reviewers.
     */
    fun enterDemo() {
        val data = DemoData.userData
        settings.writeUserDataJson(ProdJson.encodeToString(data))
        settings.writeLoginCredentials(null, null)
        settings.setDemoMode(true)
        _userData.value = data
    }

    fun logout() {
        settings.writeUserDataJson(null)
        settings.writeLoginCredentials(null, null)
        settings.setDemoMode(false)
        _userData.value = null
    }

    fun hawkCreds(): HawkCreds? {
        val rriot = _userData.value?.rriot ?: return null
        return HawkCreds(id = rriot.u, session = rriot.s, key = rriot.h)
    }

    fun token(): String? = _userData.value?.token
}
