package com.kodraliu.localrock.shared.vacuum

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the Voice Assistant page reads from and sends to one robot. */
class VoiceAssistant(private val session: VacuumSession) {

    private val _switch = MutableStateFlow<VoiceSwitch?>(null)
    val switch: StateFlow<VoiceSwitch?> = _switch

    private val _settings = MutableStateFlow(VoiceSettings())
    val settings: StateFlow<VoiceSettings> = _settings

    private val _currentSound = MutableStateFlow<CurrentSound?>(null)
    val currentSound: StateFlow<CurrentSound?> = _currentSound

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
        val sound = runCatching { session.getCurrentSound().requireOk("get_current_sound").result }.getOrNull()
        parseCurrentSound(sound)?.let { _currentSound.value = it }
        val language = parseCurrentSoundLanguage(sound) ?: old.languageCode
        val summary = runCatching { parseVoiceHistorySummary(session.getVoiceHistorySummary().requireOk("get_voice_history_summary").result) }
            .getOrNull()
        val history = when {
            summary == null || !withHistory -> old.history
            summary.count <= 0 -> emptyList()
            else -> readHistory(summary) ?: old.history
        }
        _settings.value = VoiceSettings(language, summary ?: old.summary, history)
    }

    suspend fun fetchCurrentSound(): CurrentSound? {
        val current = parseCurrentSound(session.getCurrentSound().requireOk("get_current_sound").result) ?: return null
        _currentSound.value = current
        current.language?.let { code -> _settings.update { it.copy(languageCode = code) } }
        return current
    }

    /** Sends `dnld_install_sound`, then polls until `get_current_sound` reports [pack] or the robot reports a failure. */
    suspend fun installVoicePack(
        pack: VoicePack,
        onProgress: (Int?) -> Unit,
        pollMs: Long = 2_000L,
        timeoutMs: Long = 5 * 60_000L,
    ) {
        try {
            session.installSound(pack).requireOk("dnld_install_sound")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw VoiceInstallException(VoiceInstallFailure.SEND, e)
        }
        var waited = 0L
        while (waited < timeoutMs) {
            delay(pollMs)
            waited += pollMs
            val progress = runCatching { parseSoundProgress(session.getSoundProgress().requireOk("get_sound_progress").result) }.getOrNull()
            val busy = progress?.state == 1 || progress?.state == 2
            val current = if (busy) null else runCatching { fetchCurrentSound() }.getOrNull()
            when (val step = voiceInstallStep(progress, current, pack.id)) {
                is VoiceInstallStep.Working -> onProgress(step.percent)
                VoiceInstallStep.Done -> return
                is VoiceInstallStep.Failed ->
                    throw VoiceInstallException(if (step.installFailed) VoiceInstallFailure.INSTALL else VoiceInstallFailure.DOWNLOAD)
            }
        }
        throw VoiceInstallException(VoiceInstallFailure.TIMEOUT)
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
