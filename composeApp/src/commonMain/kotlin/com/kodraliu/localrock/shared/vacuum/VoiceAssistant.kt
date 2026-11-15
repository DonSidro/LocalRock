package com.kodraliu.localrock.shared.vacuum

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the Voice Assistant page reads from and sends to one robot. */
class VoiceAssistant(private val session: VacuumSession) {

    private val _switch = MutableStateFlow<VoiceSwitch?>(null)
    val switch: StateFlow<VoiceSwitch?> = _switch

    private val _settings = MutableStateFlow(VoiceSettings())
    val settings: StateFlow<VoiceSettings> = _settings

    suspend fun fetchSwitch() {
        parseVoiceSwitch(session.getVoiceServiceSwitch().requireOk("get_voice_service_switch").result)?.let { _switch.value = it }
    }

    suspend fun setSwitch(on: Boolean) {
        val key = _switch.value?.key ?: "data"
        session.setVoiceServiceSwitch(voiceSwitchParams(key, on)).requireOk("set_voice_service_switch")
        _switch.value = VoiceSwitch(key, on)
        runCatching { fetchSwitch() }
    }

    // A read that fails keeps what an earlier read found, so a dropped reply does not hide a row.
    // History is read only for the Chat History page; the preload needs the count, not up to 10 pages.
    suspend fun fetchSettings(withHistory: Boolean = false) {
        val old = _settings.value
        val language = runCatching { parseCurrentSoundLanguage(session.getCurrentSound().requireOk("get_current_sound").result) }
            .getOrNull() ?: old.languageCode
        val summary = runCatching { parseVoiceHistorySummary(session.getVoiceHistorySummary().requireOk("get_voice_history_summary").result) }
            .getOrNull()
        val history = when {
            summary == null || !withHistory -> old.history
            summary.count <= 0 -> emptyList()
            else -> readHistory(summary) ?: old.history
        }
        _settings.value = VoiceSettings(language, summary ?: old.summary, history)
    }

    // Newest 1000 at most; a window that fails ends the read with what came before it.
    private suspend fun readHistory(summary: VoiceHistorySummary): List<VoiceMessage>? {
        val out = mutableListOf<VoiceMessage>()
        for ((min, max) in voiceHistoryWindows(summary).takeLast(10)) {
            val page = runCatching { parseVoiceHistory(session.getVoiceHistory(min, max).requireOk("get_voice_history").result) }
                .getOrNull() ?: return out.takeIf { it.isNotEmpty() }
            out += page
        }
        return out.distinctBy { it.id }.sortedBy { it.id }
    }
}
