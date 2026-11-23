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

    /** Names of the stack services that are not running, or empty when the stack can take a vacuum. */
    suspend fun stackProblems(): List<String> {
        val status = client.get("/admin/api/status").decode<StatusResponse>("Read server status")
        val services = status.health?.services ?: return listOf("the server did not report its service health")
        return REQUIRED_SERVICES.mapNotNull { name ->
            val service = services.firstOrNull { it.name == name }
            when {
                service == null -> "$name is missing"
                !service.enabled -> "$name is disabled"
                !service.running -> "$name is not running" + (service.detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
                else -> null
            }
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
    /** Server-side truth: `connected && has_public_key`. Added in local_roborock_server 1.1.0. */
    val complete: Boolean = false,
    /** `waiting` | `in_progress` | `complete` | `conflict` (1.1.0+). */
    val status: String? = null,
    val guidance: String? = null,
    val target: OnboardingTarget? = null,
)

@Serializable
data class OnboardingTarget(
    val name: String? = null,
    val duid: String? = null,
    val did: String? = null,
)

private val REQUIRED_SERVICES = listOf("https_server", "mqtt_tls_proxy", "mqtt_backend_broker")

@Serializable
private data class StatusResponse(val health: StackHealth? = null)

@Serializable
private data class StackHealth(val services: List<StackService> = emptyList())

@Serializable
private data class StackService(
    val name: String = "",
    val enabled: Boolean = true,
    val running: Boolean = false,
    val detail: String? = null,
)

@Serializable
private data class DevicesResponse(val devices: List<OnboardingDevice> = emptyList())

@Serializable
private data class LoginBody(val password: String)

@Serializable
private data class StartSessionBody(val duid: String)
