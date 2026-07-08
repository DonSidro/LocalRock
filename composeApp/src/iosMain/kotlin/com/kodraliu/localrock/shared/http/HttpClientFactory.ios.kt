package com.kodraliu.localrock.shared.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.logging.Logger

actual fun newPlatformHttpClient(block: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(Darwin, block)

actual fun platformHttpLogger(): Logger = object : Logger {
    override fun log(message: String) {
        println("[VacLocalHttp] $message")
    }
}