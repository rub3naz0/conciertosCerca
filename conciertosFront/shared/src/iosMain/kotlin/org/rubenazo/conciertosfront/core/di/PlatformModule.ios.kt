package org.rubenazo.conciertosfront.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.remote.configureConcertHttpClient
import org.rubenazo.conciertosfront.core.data.local.IosDatabaseProvider
import org.rubenazo.conciertosfront.core.data.local.getDatabaseBuilder
import org.rubenazo.conciertosfront.core.location.IosLocationProvider
import org.rubenazo.conciertosfront.core.location.LocationPort
import org.rubenazo.conciertosfront.core.map.MapLibreMapProvider
import org.rubenazo.conciertosfront.core.map.MapProvider

actual fun platformModule(): Module = module {
    single<DatabaseProviderPort> {
        IosDatabaseProvider(
            builderFactory = { getDatabaseBuilder().build() }
        )
    }
    single {
        HttpClient(Darwin) {
            configureConcertHttpClient()
        }
    }
    single<MapProvider> { MapLibreMapProvider() }
    single<LocationPort> { IosLocationProvider() }
}
