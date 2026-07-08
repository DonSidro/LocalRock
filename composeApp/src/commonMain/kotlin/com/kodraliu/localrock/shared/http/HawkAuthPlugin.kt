package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.Hawk
import com.kodraliu.localrock.shared.auth.HawkCreds
import com.kodraliu.localrock.shared.auth.HawkPayload
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
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
        check(request.method == HttpMethod.Get) {
            "HawkAuthPlugin v1 only signs GETs; method ${request.method.value} on $path needs body hashing support"
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
            payload = HawkPayload.None,
        )
        request.headers[HttpHeaders.Authorization] = header
    }
}
