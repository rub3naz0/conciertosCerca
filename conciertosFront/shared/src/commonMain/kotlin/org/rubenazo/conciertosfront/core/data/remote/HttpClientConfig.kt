package org.rubenazo.conciertosfront.core.data.remote

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared HttpClient configuration applied on every platform so Android (OkHttp) and iOS
 * (Darwin) behave identically. Without an explicit [HttpTimeout] each engine falls back to its
 * own default (OkHttp ~10s, Darwin ~60s), which made sync failures inconsistent across devices.
 */
fun HttpClientConfig<*>.configureConcertHttpClient() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
        connectTimeoutMillis = 8_000
        socketTimeoutMillis = 8_000
    }
}
