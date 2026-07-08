package com.kodraliu.localrock.shared.http

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger

actual fun newPlatformHttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient {
    Log.d("VacLocalHttp", "Creating Ktor client and applying shared configuration")

    return HttpClient(OkHttp) {
        block()
    }
}


actual fun platformHttpLogger(): Logger = object : Logger {
    override fun log(message: String) {
        message.chunked(3500).forEach { chunk ->
            Log.d("VacLocalHttp", chunk)
        }
    }
}