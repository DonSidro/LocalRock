package com.kodraliu.localrock.shared.auth

import com.kodraliu.localrock.shared.model.ApiResponse
import com.kodraliu.localrock.shared.model.UserData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class CodeLoginBody(val email: String, val code: String)

class LoginApi(private val client: HttpClient) {

    suspend fun login(email: String, code: String): UserData {
        val response: ApiResponse<UserData> = client.post("/api/v5/auth/email/login/code") {
            contentType(ContentType.Application.Json)
            setBody(CodeLoginBody(email, code))
        }.body()
        ensureOk(response)
        return response.data
    }
}

class LoginException(val responseCode: Int, message: String) : RuntimeException(message)

private fun <T> ensureOk(response: ApiResponse<T>) {
    if (response.code != 200) throw LoginException(response.code, response.msg)
}
