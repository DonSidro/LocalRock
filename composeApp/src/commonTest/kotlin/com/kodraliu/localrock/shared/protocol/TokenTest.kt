package com.kodraliu.localrock.shared.protocol

import com.kodraliu.localrock.shared.testing.toHex
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenTest {

    // Values produced by python-roborock's Utils.encode_timestamp.
    @Test
    fun encodeTimestamp_vectors() {
        assertEquals("00000000", encodeTimestamp(0x00000000).decodeToString())
        assertEquals("00010000", encodeTimestamp(0x00000001).decodeToString())
        assertEquals("56371204", encodeTimestamp(0x01234567).decodeToString())
        assertEquals("debf9a8c", encodeTimestamp(0x89abcdef.toInt()).decodeToString())
        assertEquals("eedfeadb", encodeTimestamp(0xdeadbeef.toInt()).decodeToString())
        assertEquals("20305f6a", encodeTimestamp(0x65f3a200).decodeToString())
    }

    // SALT bytes are literal "TXdfu$jyZ#TZHsg4".
    @Test
    fun salt_isExpected() {
        assertEquals("5458646675246a795a23545a48736734", SALT.toHex())
    }

    // md5(encodeTimestamp(ts) ++ localKey ++ SALT) — pinned against python-roborock.
    @Test
    fun deriveToken_vectors() {
        assertEquals("32d050fa0344d54409a480dc01881cfd", deriveToken("0123456789abcdef", 0x65f3a200).toHex())
        assertEquals("2dbdc6120e609595eec9432fae91d4bb", deriveToken("local_key", 0x00000001).toHex())
        assertEquals("a544301d156fbfe770cd2faede0135e3", deriveToken("zzzzzzzzzzzzzzzz", 0x10203040).toHex())
    }
}
