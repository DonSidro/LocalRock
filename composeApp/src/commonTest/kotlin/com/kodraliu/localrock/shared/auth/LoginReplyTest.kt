package com.kodraliu.localrock.shared.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginReplyTest {
    private fun reply(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun wrongCodeWithoutDataIsAPlainLoginError() {
        val e = assertFailsWith<LoginException> { parseLoginReply(reply("""{"code":2018,"msg":"invalid code","data":null}""")) }
        assertEquals(2018, e.responseCode)
        assertTrue(e.message!!.startsWith("That email or login code isn't right."))
        assertTrue(e.message!!.endsWith("(Server said: invalid code)"))
        assertFalse(e.message!!.contains("UserData"))
    }

    @Test
    fun successCodeWithEmptyDataIsStillALoginError() {
        val e = assertFailsWith<LoginException> { parseLoginReply(reply("""{"code":200,"msg":"success","data":{}}""")) }
        assertFalse(e.message!!.contains("UserData"))
    }

    @Test
    fun replyWithNoCodeOrDataIsALoginError() {
        assertFailsWith<LoginException> { parseLoginReply(reply("""{"msg":"nope"}""")) }
    }

    @Test
    fun successWordIsNotEchoedAsTheReason() {
        assertEquals(
            "That email or login code isn't right. Check the login code set on your LocalRock server.",
            loginFailureMessage("success"),
        )
    }
}
