package com.kodraliu.localrock.shared.vacuum

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatterTest {
    private fun parse(json: String) = parseMatterStatus(Json.parseToJsonElement(json))

    @Test
    fun readsCodesFromAnObjectOrAOneItemList() {
        val body = """{"status":"Ready","ManualPairingCode":"34970112332","QrCode":"MT:Y.K9042C00KA0648G00"}"""
        val expected = MatterInfo(MatterState.Ready, "34970112332", "MT:Y.K9042C00KA0648G00")
        assertEquals(expected, parse(body))
        assertEquals(expected, parse("[$body]"))
    }

    @Test
    fun missingKeyHasNoCodes() {
        val info = parse("""[{"status":"KeyNotExit"}]""")!!
        assertEquals(MatterState.KeyNotExit, info.state)
        assertNull(info.manualCode)
        assertNull(info.qrPayload)
    }

    @Test
    fun anUnknownStatusIsKeptAsNull() {
        assertNull(parse("""{"status":"SomethingNew"}""")!!.state)
    }

    @Test
    fun nonObjectRepliesAreNotStatuses() {
        assertNull(parse("""["ok"]"""))
        assertNull(parse("[]"))
    }

    @Test
    fun failedAndCodeStates() {
        assertTrue(MatterState.KeyDownloadFailure.failed)
        assertTrue(MatterState.KeyCorrupt.failed)
        assertFalse(MatterState.KeyDownloading.failed)
        assertTrue(MatterState.Ready.hasCodes)
        assertTrue(MatterState.Joined.hasCodes)
        assertFalse(MatterState.Preparing.hasCodes)
    }
}
