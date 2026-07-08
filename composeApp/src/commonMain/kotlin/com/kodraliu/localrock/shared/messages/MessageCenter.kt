package com.kodraliu.localrock.shared.messages

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class MessageSeverity { ERROR, WARNING, INFO, SUCCESS }

data class AppMessage(
    val id: Long,
    val text: String,
    val severity: MessageSeverity,
)


data class AppBanner(
    val key: String,
    val text: String,
    val severity: MessageSeverity,
    val dismissible: Boolean = true,
)


class MessageCenter {
    private val _transient = MutableSharedFlow<AppMessage>(extraBufferCapacity = 32)
    val transient: SharedFlow<AppMessage> = _transient

    private val _banners = MutableStateFlow<List<AppBanner>>(emptyList())
    val banners: StateFlow<List<AppBanner>> = _banners

    private var counter = 0L

    fun post(text: String, severity: MessageSeverity) {
        if (text.isBlank()) return
        _transient.tryEmit(AppMessage(++counter, text, severity))
    }

    fun error(text: String) = post(text, MessageSeverity.ERROR)
    fun warn(text: String) = post(text, MessageSeverity.WARNING)
    fun info(text: String) = post(text, MessageSeverity.INFO)
    fun success(text: String) = post(text, MessageSeverity.SUCCESS)

    fun showBanner(key: String, text: String, severity: MessageSeverity, dismissible: Boolean = true) {
        _banners.update { list ->
            list.filterNot { it.key == key } + AppBanner(key, text, severity, dismissible)
        }
    }

    fun dismissBanner(key: String) {
        _banners.update { list -> list.filterNot { it.key == key } }
    }

    companion object {
        const val BANNER_MQTT = "mqtt-connection"
    }
}
