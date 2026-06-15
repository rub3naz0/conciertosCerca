package org.rubenazo.conciertosfront.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import org.rubenazo.conciertosfront.core.data.local.AndroidDatabaseProvider
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.local.getDatabaseBuilder
import org.rubenazo.conciertosfront.core.data.remote.configureConcertHttpClient
import org.rubenazo.conciertosfront.core.location.AndroidLocationProvider
import org.rubenazo.conciertosfront.core.location.LocationPort
import org.rubenazo.conciertosfront.core.map.MapLibreMapProvider
import org.rubenazo.conciertosfront.core.map.MapProvider

actual fun platformModule(): Module = module {
    single<DatabaseProviderPort> {
        val context = androidContext()
        AndroidDatabaseProvider(
            context = context,
            builderFactory = { getDatabaseBuilder(context).build() }
        )
    }
    single {
        HttpClient(OkHttp) {
            configureConcertHttpClient()
        }
    }
    single<MapProvider> { MapLibreMapProvider() }
    single<LocationPort> { AndroidLocationProvider(androidContext()) }
}
