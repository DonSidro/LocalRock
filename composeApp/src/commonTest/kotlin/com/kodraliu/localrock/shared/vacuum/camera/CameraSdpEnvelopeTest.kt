package com.kodraliu.localrock.shared.vacuum.camera

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*

@OptIn(ExperimentalEncodingApi::class)
class CameraSdpEnvelopeTest {
    private val sdp = "v=0\r\ns=quoted \"name\" and literal \\r\\n\r\n"
    private fun decoded(value: String) = Base64.decode(value).decodeToString()

    @Test fun standard_offer_preserves_sdp_and_uses_oem_field_order() {
        val text = decoded(encodeCameraOffer(sdp, false))
        assertTrue(text.startsWith("{\"sdp\":"))
        assertFalse(text.contains('\r'))
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("sdp", "type"), obj.keys)
        assertEquals(sdp, obj["sdp"]!!.jsonPrimitive.content)
        assertEquals("offer", obj["type"]!!.jsonPrimitive.content)
    }

    @Test fun legacy_offer_contains_only_real_line_breaks_and_still_escapes_quotes_and_backslashes() {
        val text = decoded(encodeCameraOffer(sdp, true))
        assertEquals("{\"sdp\":\"v=0\r\ns=quoted \\\"name\\\" and literal \\\\r\\\\n\r\n\",\"type\":\"offer\"}", text)
        assertTrue(text.length < decoded(encodeCameraOffer(sdp, false)).length)
    }

    @Test fun accepts_standard_and_legacy_answers_without_changing_the_sdp() {
        for (legacy in listOf(false, true)) {
            val text = decoded(encodeCameraOffer(sdp, legacy)).replace("\"type\":\"offer\"", "\"type\":\"answer\"")
            assertEquals(sdp, decodeCameraAnswer(Base64.encode(text.encodeToByteArray())))
        }
    }

    @Test fun rejects_wrong_types_missing_sdp_and_malformed_messages_without_leaking_payloads() {
        for (text in listOf("{}", "[]", "{\"type\":\"offer\",\"sdp\":\"private-sdp\"}",
            "{\"type\":\"answer\",\"sdp\":1}", "{\"type\":\"answer\",\"sdp\":\"\"}",
            "{\"type\":\"answer\",\"sdp\":\"private\tbad\"}", "private-invalid-json")) {
            val error = assertFailsWith<CameraFailure> { decodeCameraAnswer(Base64.encode(text.encodeToByteArray())) }
            assertEquals("The robot returned an invalid video answer.", error.message)
        }
    }
}
