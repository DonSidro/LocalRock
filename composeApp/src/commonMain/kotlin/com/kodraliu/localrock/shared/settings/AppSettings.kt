package com.kodraliu.localrock.shared.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private const val KEY_SERVER_BASE_URL = "server_base_url"
private const val KEY_USER_DATA = "user_data_json"
private const val KEY_THEME_MODE = "theme_mode"
private const val KEY_ADMIN_PASSWORD = "admin_password"
private const val KEY_INSTALL_ID = "install_id"
private const val KEY_INTRO_SEEN = "intro_seen"
private const val KEY_DEMO_MODE = "demo_mode"

class AppSettings(private val settings: Settings) {


    val installId: String = settings.getStringOrNull(KEY_INSTALL_ID) ?: run {
        val id = buildString { repeat(8) { append("0123456789abcdef"[Random.nextInt(16)]) } }
        settings.putString(KEY_INSTALL_ID, id)
        id
    }

    private val _serverBaseUrl =
        MutableStateFlow(settings.getStringOrNull(KEY_SERVER_BASE_URL))
    val serverBaseUrl: StateFlow<String?> = _serverBaseUrl.asStateFlow()

    private val _introSeen = MutableStateFlow(settings.getBoolean(KEY_INTRO_SEEN, false))
    val introSeen: StateFlow<Boolean> = _introSeen.asStateFlow()

    fun setIntroSeen(value: Boolean) {
        settings.putBoolean(KEY_INTRO_SEEN, value)
        _introSeen.value = value
    }

    /** True while the app is in offline demo mode (no server/MQTT; see DemoData). */
    private val _demoMode = MutableStateFlow(settings.getBoolean(KEY_DEMO_MODE, false))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    fun setDemoMode(value: Boolean) {
        settings.putBoolean(KEY_DEMO_MODE, value)
        _demoMode.value = value
    }

    private val _themeMode = MutableStateFlow(
        settings.getStringOrNull(KEY_THEME_MODE)
            ?.runCatching { ThemeMode.valueOf(this) }?.getOrNull()
            ?: ThemeMode.SYSTEM
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        settings.putString(KEY_THEME_MODE, mode.name)
        _themeMode.value = mode
    }

    fun setServerBaseUrl(url: String?) {
        if (url.isNullOrBlank()) {
            settings.remove(KEY_SERVER_BASE_URL)
            _serverBaseUrl.value = null
        } else {
            settings.putString(KEY_SERVER_BASE_URL, url)
            _serverBaseUrl.value = url
        }
    }

    private val _adminPassword = MutableStateFlow(settings.getStringOrNull(KEY_ADMIN_PASSWORD))
    val adminPassword: StateFlow<String?> = _adminPassword.asStateFlow()

    fun setAdminPassword(password: String?) {
        if (password.isNullOrBlank()) {
            settings.remove(KEY_ADMIN_PASSWORD)
            _adminPassword.value = null
        } else {
            settings.putString(KEY_ADMIN_PASSWORD, password)
            _adminPassword.value = password
        }
    }

    fun cameraPin(duid: String): String? = settings.getStringOrNull("camera_pin_$duid")

    fun setCameraPin(duid: String, pin: String?) {
        if (pin.isNullOrBlank()) settings.remove("camera_pin_$duid")
        else settings.putString("camera_pin_$duid", pin)
    }

    internal fun readUserDataJson(): String? = settings.getStringOrNull(KEY_USER_DATA)

    internal fun writeUserDataJson(json: String?) {
        if (json == null) settings.remove(KEY_USER_DATA) else settings.putString(KEY_USER_DATA, json)
    }
}
