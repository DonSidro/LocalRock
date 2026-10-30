package com.kodraliu.localrock.shared.auth

import com.kodraliu.localrock.shared.http.ProdJson
import com.kodraliu.localrock.shared.http.serverErrorText
import com.kodraliu.localrock.shared.model.UserData
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
private data class CodeLoginBody(val email: String, val code: String)

/** Only `code`/`msg` are guaranteed; `data` is absent or null on error replies. */
@Serializable
private data class LoginEnvelope(
    val code: Int = 0,
    val msg: String = "",
    val data: JsonElement? = null,
)

class LoginApi(private val client: HttpClient) {

    suspend fun login(email: String, code: String): UserData {
        // Read the body as text and decode the envelope first with `data` left raw: error replies
        // (e.g. new_connections_disabled) contain no UserData, and decoding straight into
        // ApiResponse<UserData> would fail on the missing uid/rruid/... before the server's own
        // message could be read, hiding the real error.
        val response = client.post("/api/v5/auth/email/login/code") {
            contentType(ContentType.Application.Json)
            setBody(CodeLoginBody(email, code))
        }
        val httpStatus = response.status.value
        val text = response.bodyAsText()
        val envelope = try {
            ProdJson.decodeFromString<LoginEnvelope>(text)
        } catch (e: SerializationException) {
            throw LoginException(httpStatus, null, text.replace(Regex("\\s+"), " ").trim().take(120))
        }
        if (envelope.code != 200 || !response.status.isSuccess()) {
            throw LoginException(httpStatus, envelope.code, serverErrorText(envelope.msg, envelope.data))
        }
        val data = envelope.data
            ?: throw LoginException(httpStatus, envelope.code, "Login response contained no data")
        return ProdJson.decodeFromJsonElement(UserData.serializer(), data)
    }
}

/**
 * [httpStatus] is the HTTP status line, [responseCode] the server's own `code` field (null when the
 * body wasn't the usual envelope).
 */
class LoginException(
    val httpStatus: Int,
    val responseCode: Int?,
    val serverMessage: String,
) : RuntimeException(
    buildString {
        append("HTTP ").append(httpStatus)
        if (responseCode != null) append(", code ").append(responseCode)
        if (serverMessage.isNotEmpty()) append(": ").append(serverMessage)
    }
) {
    val isCredentialsRejected: Boolean get() = httpStatus == 401
}
