package com.kodraliu.localrock.shared.auth

import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.model.UserData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
private data class CodeLoginBody(val email: String, val code: String)

class LoginApi(private val client: HttpClient) {

    suspend fun login(email: String, code: String): UserData {
        val reply: JsonObject = client.post("/api/v5/auth/email/login/code") {
            contentType(ContentType.Application.Json)
            setBody(CodeLoginBody(email, code))
        }.body()
        return parseLoginReply(reply)
    }
}

class LoginException(val responseCode: Int, message: String) : RuntimeException(message)

/** The user data from a login reply, or a [LoginException] a person can act on when the server refused. */
internal fun parseLoginReply(reply: JsonObject): UserData {
    val code = (reply["code"] as? JsonPrimitive)?.intOrNull ?: -1
    val msg = (reply["msg"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val data = reply["data"]?.takeIf { it != JsonNull }
    // A refused login carries no user data, so the code is checked before decoding, not after it fails.
    if (code != 200 || data !is JsonObject) throw LoginException(code, loginFailureMessage(msg))
    return try {
        ProdJson.decodeFromJsonElement(UserData.serializer(), data)
    } catch (e: Exception) {
        throw LoginException(code, "The server accepted the login but sent back incomplete account data. Check that your LocalRock server is up to date.")
    }
}

internal fun loginFailureMessage(msg: String): String {
    val detail = msg.trim().takeIf { it.isNotEmpty() && !it.equals("success", ignoreCase = true) }
    return "That email or login code isn't right. Check the login code set on your LocalRock server." +
        (detail?.let { " (Server said: $it)" } ?: "")
}
