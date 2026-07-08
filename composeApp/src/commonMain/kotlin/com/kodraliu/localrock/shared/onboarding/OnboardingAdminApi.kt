package com.kodraliu.localrock.shared.onboarding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


class OnboardingAdminApi(private val client: HttpClient) {

    suspend fun login(password: String) {
        val resp = client.post("/admin/api/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginBody(password))
        }
        if (resp.status == HttpStatusCode.Unauthorized) {
            throw OnboardingAdminException("Invalid admin password")
        }
        if (!resp.status.isSuccess()) {
            throw OnboardingAdminException("Admin login failed (HTTP ${resp.status.value})")
        }
    }

    suspend fun listDevices(): List<OnboardingDevice> =
        client.get("/admin/api/onboarding/devices").decode<DevicesResponse>("List vacuums").devices

    suspend fun startSession(duid: String): OnboardingSession =
        client.post("/admin/api/onboarding/sessions") {
            contentType(ContentType.Application.Json)
            setBody(StartSessionBody(duid))
        }.decode("Start onboarding session")

    suspend fun getSession(sessionId: String): OnboardingSession =
        client.get("/admin/api/onboarding/sessions/${sessionId.encodeURLPathPart()}")
            .decode("Read onboarding session")

    suspend fun deleteSession(sessionId: String) {
        client.delete("/admin/api/onboarding/sessions/${sessionId.encodeURLPathPart()}")
    }

    private suspend inline fun <reified T> HttpResponse.decode(what: String): T {
        if (status == HttpStatusCode.Unauthorized) {
            throw OnboardingAdminException("$what failed: not signed in to the admin API (log in again)")
        }
        if (!status.isSuccess()) {
            throw OnboardingAdminException("$what failed (HTTP ${status.value})")
        }
        return body()
    }
}

class OnboardingAdminException(message: String) : RuntimeException(message)

@Serializable
data class OnboardingDevice(
    val duid: String,
    val name: String? = null,
    val connected: Boolean = false,
    val onboarding: OnboardingInfo? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: duid
}

@Serializable
data class OnboardingInfo(
    @SerialName("has_public_key") val hasPublicKey: Boolean = false,
    val unsupported: Boolean = false,
    val guidance: String? = null,
    @SerialName("key_state") val keyState: KeyState? = null,
)

@Serializable
data class KeyState(
    @SerialName("query_samples") val querySamples: Int = 0,
)

@Serializable
data class OnboardingSession(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("query_samples") val querySamples: Int = 0,
    @SerialName("has_public_key") val hasPublicKey: Boolean = false,
    @SerialName("public_key_state") val publicKeyState: String? = null,
    val connected: Boolean = false,
    val guidance: String? = null,
    val target: OnboardingTarget? = null,
)

@Serializable
data class OnboardingTarget(
    val name: String? = null,
    val duid: String? = null,
    val did: String? = null,
)

@Serializable
private data class DevicesResponse(val devices: List<OnboardingDevice> = emptyList())

@Serializable
private data class LoginBody(val password: String)

@Serializable
private data class StartSessionBody(val duid: String)
