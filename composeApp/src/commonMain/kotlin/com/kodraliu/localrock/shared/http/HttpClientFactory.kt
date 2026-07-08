package com.kodraliu.localrock.shared.http

import com.kodraliu.localrock.shared.auth.HawkCreds
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val ProdJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
}

class HttpClientConfig(
    val baseUrlProvider: () -> String?,
    val credsProvider: () -> HawkCreds?,
)

expect fun platformHttpLogger(): Logger

expect fun newPlatformHttpClient(
    block: io.ktor.client.HttpClientConfig<*>.() -> Unit
): HttpClient

fun buildVacLocalHttpClient(config: HttpClientConfig): HttpClient =
    newPlatformHttpClient {
        install(ContentNegotiation) {
            json(ProdJson)
        }

        install(Logging) {
            logger = platformHttpLogger()

            level = LogLevel.NONE

            sanitizeHeader { header ->
                header == HttpHeaders.Authorization ||
                        header == HttpHeaders.Cookie
            }
        }

        install(HawkAuth) {
            credsProvider = config.credsProvider
        }

        install(DefaultRequest) {
            val base = config.baseUrlProvider()

            if (!base.isNullOrBlank()) {
                url.takeFrom(base)

                if (url.protocol == URLProtocol.HTTP) {
                    url.protocol = URLProtocol.HTTPS
                }
            }
        }
    }


fun buildAdminHttpClient(config: HttpClientConfig): HttpClient =
    newPlatformHttpClient {
        install(ContentNegotiation) {
            json(ProdJson)
        }

        install(Logging) {
            logger = platformHttpLogger()
            level = LogLevel.INFO

            sanitizeHeader { header ->
                header == HttpHeaders.Authorization ||
                        header == HttpHeaders.Cookie ||
                        header == HttpHeaders.SetCookie
            }
        }

        install(HttpCookies)

        install(DefaultRequest) {
            val base = config.baseUrlProvider()

            if (!base.isNullOrBlank()) {
                url.takeFrom(base)

                if (url.protocol == URLProtocol.HTTP) {
                    url.protocol = URLProtocol.HTTPS
                }
            }
        }
    }