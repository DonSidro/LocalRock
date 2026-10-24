package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.Hawk
import com.kodraliu.localrock.shared.auth.HawkCreds
import com.kodraliu.localrock.shared.auth.HawkPayload
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlin.random.Random

internal expect fun currentEpochSeconds(): Long

class HawkAuthConfig {
    var credsProvider: () -> HawkCreds? = { null }
    var nowSeconds: () -> Long = ::currentEpochSeconds
    var nonceProvider: () -> String = { defaultNonce() }
}

private fun defaultNonce(): String {
    val bytes = ByteArray(8).also { Random.nextBytes(it) }
    return bytes.joinToString("") { ((it.toInt() and 0xFF).toString(16)).padStart(2, '0') }
}


private val PUBLIC_PATH_PREFIXES = listOf("/api/")
private val PUBLIC_EXACT_PATHS = setOf("/nc/prepare", "/region", "/time", "/location")

internal fun isHawkPublicPath(path: String): Boolean {
    val p = path.trimEnd('/')
    if (PUBLIC_EXACT_PATHS.contains(p)) return true
    return PUBLIC_PATH_PREFIXES.any { p.startsWith(it) }
}

val HawkAuth = createClientPlugin("HawkAuth", ::HawkAuthConfig) {
    val credsProvider = pluginConfig.credsProvider
    val nowSeconds = pluginConfig.nowSeconds
    val nonceProvider = pluginConfig.nonceProvider

    onRequest { request, _ ->
        val path = Url(request.url.buildString()).encodedPath
        if (isHawkPublicPath(path)) return@onRequest
        val creds = credsProvider() ?: return@onRequest
        // JSON writes hash the exact outgoing UTF-8 bytes. Form POSTs retain the server's
        // sorted k=v hash. Reject unknown body types instead of silently signing them wrongly.
        val payload = when {
            request.method == HttpMethod.Get -> HawkPayload.None
            request.method in listOf(HttpMethod.Post, HttpMethod.Delete) && request.body is EmptyContent -> HawkPayload.None
            request.method in listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch) &&
                request.body is TextContent &&
                (request.body as TextContent).contentType.match(ContentType.Application.Json) ->
                HawkPayload.Json((request.body as TextContent).bytes())
            request.method == HttpMethod.Post && request.body is FormDataContent ->
                HawkPayload.Form(formPairs((request.body as FormDataContent).formData))
            else -> error(
                "Unsupported Hawk request body for ${request.method.value} on $path"
            )
        }
        val query = buildMap {
            request.url.parameters.entries().forEach { (k, v) ->
                if (v.isNotEmpty()) put(k, v.first())
            }
        }
        val header = Hawk.authorizationHeader(
            creds = creds,
            path = path,
            ts = nowSeconds(),
            nonce = nonceProvider(),
            query = query,
            payload = payload,
        )
        request.headers[HttpHeaders.Authorization] = header
    }
}

/** Form fields as the server hashes them: first value per key (a blank value still counts). */
internal fun formPairs(formData: Parameters): Map<String, String> = buildMap {
    formData.entries().forEach { (k, v) -> if (v.isNotEmpty()) put(k, v.first()) }
}

