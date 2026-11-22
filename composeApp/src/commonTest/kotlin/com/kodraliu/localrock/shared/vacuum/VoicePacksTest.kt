package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoicePacksTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    private val info = """
        {"voice_pkg_version":1,"voice_pkg_host":"us-app.roborock.com","voice_list":[
          {"voice_id":12,"version":2,"applicable":["us","de"],"lang":"de","voice_pri":9,"voice_title":"Deutsch",
           "voice_pkg_url":"https://srv/voice-pkg/files/us-app.roborock.com/app/beryllite/voice-pkg/package/de.pkg","voice_pkg_md5":"bb"},
          {"voice_id":3,"version":7,"applicable":["us"],"lang":"en","voice_pri":3,"voice_title":"English","voice_sub_title":"female voice",
           "voice_pkg_url":"https://srv/voice-pkg/files/us-app.roborock.com/app/beryllite/voice-pkg/package/en.pkg","voice_pkg_md5":"aa"},
          {"voice_id":1,"version":1,"applicable":["prc"],"lang":"prc","voice_pri":1,"voice_title":"标准普通话版",
           "voice_pkg_url":"https://srv/x/prc.pkg","voice_pkg_md5":"cc"},
          {"voice_id":4,"voice_title":"No url"}
        ]}
    """.trimIndent()

    @Test
    fun listsThePacksForTheRobotsLocationByPriority() {
        val packs = parseVoicePackList(json(info), "us")!!
        assertEquals(listOf(3, 12), packs.map { it.id })
        val en = packs.first()
        assertEquals("English", en.title)
        assertEquals("female voice", en.subTitle)
        assertEquals(7, en.version)
        assertEquals("aa", en.md5)
        assertEquals("en", en.lang)
    }

    @Test
    fun cnRobotsSeeThePrcPacksAndAMissingListIsNull() {
        assertEquals(listOf(1), parseVoicePackList(json(info), "cn")!!.map { it.id })
        assertNull(parseVoicePackList(json("""{"code":502}"""), "us"))
        val cnLike = """{"voice_list":[{"voice_id":5,"applicable":["prc"],"voice_title":"Prc","voice_pkg_url":"u","voice_pkg_md5":"m"},{"voice_id":6,"applicable":["cncn"],"voice_title":"Other","voice_pkg_url":"u","voice_pkg_md5":"m"}]}"""
        assertEquals(listOf(5), parseVoicePackList(json(cnLike), "CN")!!.map { it.id })
        assertEquals(listOf(6), parseVoicePackList(json(cnLike), "cncn")!!.map { it.id })
        val noApplicable = """{"voice_list":[{"voice_id":3,"applicable":null,"voice_title":"English","voice_pkg_url":"u","voice_pkg_md5":"m"}]}"""
        assertEquals(1, parseVoicePackList(json(noApplicable), "us")!!.size)
    }

    @Test
    fun readsTheCurvsCurrentSound() {
        val reply = """[{"sid_in_use":3,"sid_version":7,"sid_in_progress":0,"location":"us","bom":"A.03.0777","language":"en","msg_ver":2}]"""
        assertEquals(CurrentSound(3, 7, 0, "us", "en"), parseCurrentSound(json(reply)))
        assertNull(parseCurrentSound(json("""["ok"]""")))
    }

    @Test
    fun readsSoundProgress() {
        assertEquals(SoundProgress(12, 45, 1, 0), parseSoundProgress(json("""[{"sid_in_progress":12,"progress":45,"state":1,"error":0}]""")))
        assertNull(parseSoundProgress(json("""[{"sid_in_progress":12}]""")))
    }

    @Test
    fun idlePollsCountOnlyConsecutiveIdleReads() {
        val idle = CurrentSound(3, 7, 0, "us", "en")
        var polls = 0
        var step: VoiceInstallStep = VoiceInstallStep.Working(null)
        repeat(VOICE_INSTALL_IDLE_POLLS) {
            step = voiceInstallStep(SoundProgress(0, 0, 0, 0), idle, 12, polls + 1)
            polls = nextVoiceIdlePolls(step, idle, polls)
        }
        assertEquals(VoiceInstallStep.Failed(installFailed = false), step)
        assertEquals(VOICE_INSTALL_IDLE_POLLS - 1, nextVoiceIdlePolls(VoiceInstallStep.Working(null), idle, VOICE_INSTALL_IDLE_POLLS - 2))
        assertEquals(0, nextVoiceIdlePolls(VoiceInstallStep.Working(null), null, 2))
        assertEquals(0, nextVoiceIdlePolls(VoiceInstallStep.Working(40), idle, 2))
    }

    @Test
    fun installStepsFollowThePluginsProgressRules() {
        val idle = CurrentSound(3, 7, 0, "us", "en")
        val switched = CurrentSound(12, 2, 0, "us", "de")
        assertEquals(VoiceInstallStep.Working(45), voiceInstallStep(SoundProgress(12, 45, 1, 0), null, 12))
        assertEquals(VoiceInstallStep.Working(100), voiceInstallStep(SoundProgress(12, 100, 2, 0), null, 12))
        assertEquals(VoiceInstallStep.Done, voiceInstallStep(SoundProgress(12, 100, 3, 0), switched, 12))
        assertEquals(VoiceInstallStep.Done, voiceInstallStep(null, switched, 12))
        assertEquals(VoiceInstallStep.Working(null), voiceInstallStep(SoundProgress(0, 0, 0, 0), idle, 12))
        assertEquals(VoiceInstallStep.Working(null), voiceInstallStep(SoundProgress(0, 0, 0, 0), idle, 12, VOICE_INSTALL_IDLE_POLLS - 1))
        assertEquals(VoiceInstallStep.Failed(installFailed = false), voiceInstallStep(SoundProgress(0, 0, 0, 0), idle, 12, VOICE_INSTALL_IDLE_POLLS))
        assertEquals(VoiceInstallStep.Working(null), voiceInstallStep(null, null, 12, VOICE_INSTALL_IDLE_POLLS))
        assertEquals(VoiceInstallStep.Done, voiceInstallStep(null, switched, 12, VOICE_INSTALL_IDLE_POLLS))
        assertEquals(VoiceInstallStep.Failed(installFailed = false), voiceInstallStep(SoundProgress(12, 0, 4, 2), idle, 12))
        assertEquals(VoiceInstallStep.Failed(installFailed = false), voiceInstallStep(SoundProgress(12, 0, 0, 13), idle, 12))
        assertEquals(VoiceInstallStep.Failed(installFailed = true), voiceInstallStep(SoundProgress(12, 0, 4, 5), idle, 12))
    }

    @Test
    fun installParamsMatchThePlugin() {
        val pack = parseVoicePackList(json(info), "us")!!.first()
        val params = installSoundParams(pack)
        assertEquals(JsonPrimitive(3), params["sid"])
        assertEquals(JsonPrimitive("https://srv/voice-pkg/files/us-app.roborock.com/app/beryllite/voice-pkg/package/en.pkg"), params["url"])
        assertEquals(JsonPrimitive("aa"), params["md5"])
        assertEquals(JsonPrimitive(7), params["sver"])
        assertEquals(JsonPrimitive(""), params["ota_url"])
    }
}
