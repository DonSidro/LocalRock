package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test
    fun readsTheSummaryAsAnObjectOrAOneItemList() {
        val expected = VoiceHistorySummary(1, 1, 1)
        assertEquals(expected, parseVoiceHistorySummary(json("""{"min_id":1,"max_id":1,"count":1}""")))
        assertEquals(expected, parseVoiceHistorySummary(json("""[{"min_id":1,"max_id":1,"count":1}]""")))
        assertNull(parseVoiceHistorySummary(json("""["ok"]""")))
        assertNull(parseVoiceHistorySummary(null))
    }

    @Test
    fun readsTheHistoryTheCurvSent() {
        val reply = """{"history":[{"id":1,"ts":1794594183,"text":"I cannot understand this command, please open the APP to find supported voice commands.","speaker":2,"offline":2}]}"""
        val history = parseVoiceHistory(json(reply))!!
        assertEquals(1, history.size)
        val m = history.single()
        assertEquals(1, m.id)
        assertEquals(1794594183L, m.ts)
        assertEquals(2, m.speaker)
        assertEquals(2, m.offline)
        assertEquals(false, m.fromUser)
        assertTrue(m.text.startsWith("I cannot understand"))
    }

    @Test
    fun speakerOneIsTheUser() {
        val history = parseVoiceHistory(json("""{"history":[{"id":3,"ts":5,"text":"pause","speaker":1}]}"""))!!
        assertTrue(history.single().fromUser)
        assertNull(history.single().offline)
    }

    @Test
    fun aReplyWithoutHistoryIsNotAnEmptyList() {
        assertNull(parseVoiceHistory(json("""["ok"]""")))
        assertEquals(emptyList(), parseVoiceHistory(json("""{"history":[]}""")))
    }

    @Test
    fun readsIdsInWindowsOfOneHundred() {
        assertEquals(listOf(1 to 101), voiceHistoryWindows(VoiceHistorySummary(1, 1, 1)))
        assertEquals(listOf(5 to 105, 105 to 205, 205 to 305), voiceHistoryWindows(VoiceHistorySummary(5, 230, 226)))
        assertEquals(emptyList(), voiceHistoryWindows(VoiceHistorySummary(0, 0, 0)))
    }

    @Test
    fun readsTheLanguageFromGetCurrentSound() {
        val reply = """[{"sid_in_use":3,"sid_version":7,"sid_in_progress":0,"location":"us","bom":"A.03.0777","language":"en","msg_ver":2}]"""
        assertEquals("en", parseCurrentSoundLanguage(json(reply)))
        assertNull(parseCurrentSoundLanguage(json("""[{"sid_in_use":3}]""")))
        assertNull(parseCurrentSoundLanguage(json("""[{"language":""}]""")))
    }

    @Test
    fun namesLanguagesAsTheOfficialLanguagePage() {
        assertEquals("English", voiceLanguageName("en"))
        assertEquals("English", voiceLanguageName("en-uk"))
        assertEquals("Español", voiceLanguageName("es-la"))
        assertEquals("Deutsch", voiceLanguageName("de_transformer"))
        assertEquals("Українська", voiceLanguageName("uk"))
        assertEquals("臺灣普通話版", voiceLanguageName("tw"))
        assertEquals("xx", voiceLanguageName("xx"))
    }

    @Test
    fun stampsUserLinesOnlyWhenAMinuteHasPassed() {
        fun m(id: Int, ts: Long, speaker: Int) = VoiceMessage(id, ts, "t", speaker, null)
        val messages = listOf(m(1, 1000, 1), m(2, 1002, 2), m(3, 1030, 1), m(4, 1100, 1), m(5, 1300, 2), m(6, 1310, 1))
        assertEquals(listOf(true, false, false, true, false, false), voiceTimestampsShown(messages))
    }

    @Test
    fun formatsTheStampAsTheOfficialChat() {
        assertEquals("2026/11/12 09:03", formatVoiceTimestamp(1794474180, 0))
        assertEquals("2026/11/12 04:03", formatVoiceTimestamp(1794474180, -5 * 3600))
        assertEquals("2026/11/11 23:30", formatVoiceTimestamp(1794474180, -(9 * 3600 + 33 * 60)))
        assertEquals("2024/2/29 00:00", formatVoiceTimestamp(1709164800, 0))
        assertEquals("1970/1/1 00:00", formatVoiceTimestamp(0, 0))
    }

    @Test
    fun readsTheVoiceSwitch() {
        assertEquals(VoiceSwitch("data", true), parseVoiceSwitch(json("""{"data":1}""")))
        assertEquals(VoiceSwitch("data", false), parseVoiceSwitch(json("""[{"data":0}]""")))
        assertNull(parseVoiceSwitch(json("""{"data":7}""")))
        assertNull(parseVoiceSwitch(json("""["ok"]""")))
    }

    @Test
    fun sendsTheVoiceSwitchAsTheBareObject() {
        assertEquals("""{"data":0}""", voiceSwitchParams("data", false).toString())
        assertEquals("""{"data":1}""", voiceSwitchParams("data", true).toString())
    }

    private fun msg(id: Int) = VoiceMessage(id, 0L, "m$id", 1, null)

    @Test
    fun historyReadsNewestFirstAndRetriesAFailedWindowOnce() = runTest {
        val windows = listOf(0 to 100, 100 to 200, 200 to 300)
        val calls = mutableListOf<Int>()
        var failedOnce = false
        val read = readVoiceHistory(windows) { min, _ ->
            calls += min
            if (min == 200 && !failedOnce) { failedOnce = true; null } else listOf(msg(min + 1))
        }
        assertEquals(listOf(200, 200, 100, 0), calls)
        assertEquals(listOf(1, 101, 201), read!!.map { it.id })
    }

    @Test
    fun aWindowThatFailsTwiceKeepsTheNewerLines() = runTest {
        val windows = listOf(0 to 100, 100 to 200, 200 to 300)
        val read = readVoiceHistory(windows) { min, _ -> if (min == 0) null else listOf(msg(min + 1)) }
        assertEquals(listOf(101, 201), read!!.map { it.id })
        assertNull(readVoiceHistory(windows) { _, _ -> null })
        assertEquals(emptyList(), readVoiceHistory(windows) { _, _ -> emptyList() })
    }

    @Test
    fun onlyTheCurvHasAVoiceAssistant() {
        // Feature sets the Curv 2 Flow and a Qrevo Pro answered to app_get_init_status.
        assertTrue(isVoiceAssistantSupported("00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF"))
        assertFalse(isVoiceAssistantSupported("42BA8F5C7EDAFFFE"))
        assertFalse(isVoiceAssistantSupported(null))
    }

    @Test
    fun theVoiceBitIsNotListedAsAMissingControl() {
        assertTrue(unlistedAdvertisedFeatures("00000000000202058CC57FFDA86836DD5BBFAF7F7EFEFFFF").none { it.bit == VOICE_ASSISTANT_BIT })
    }
}
