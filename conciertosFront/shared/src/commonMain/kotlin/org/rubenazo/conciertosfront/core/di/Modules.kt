package org.rubenazo.conciertosfront.core.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.rubenazo.conciertosfront.core.config.AppConfig
import org.rubenazo.conciertosfront.core.data.local.DatabaseProviderPort
import org.rubenazo.conciertosfront.core.data.remote.api.ConcertApi
import org.rubenazo.conciertosfront.core.data.remote.api.ConcertApiClient
import org.rubenazo.conciertosfront.core.data.repository.ArtistRepositoryImpl
import org.rubenazo.conciertosfront.core.data.repository.ConcertRepositoryImpl
import org.rubenazo.conciertosfront.core.data.repository.SalaConciertoRepositoryImpl
import org.rubenazo.conciertosfront.core.data.repository.SyncRepositoryImpl
import org.rubenazo.conciertosfront.core.domain.repository.ArtistRepository
import org.rubenazo.conciertosfront.core.util.DateProvider
import org.rubenazo.conciertosfront.core.util.DefaultDateProvider
import org.rubenazo.conciertosfront.core.domain.repository.ConcertRepository
import org.rubenazo.conciertosfront.core.domain.repository.SalaConciertoRepository
import org.rubenazo.conciertosfront.core.domain.repository.SyncRepository
import org.rubenazo.conciertosfront.feature.artistas.ArtistasViewModel
import org.rubenazo.conciertosfront.feature.conciertos.ConcertosViewModel
import org.rubenazo.conciertosfront.feature.mapa.MapaViewModel
import org.rubenazo.conciertosfront.feature.salas.SalasViewModel
import org.rubenazo.conciertosfront.feature.sync.SyncViewModel

val commonModule = module {
    single<ConcertApiClient> { ConcertApi(AppConfig.BASE_URL, get()) }
    single<SalaConciertoRepository> { SalaConciertoRepositoryImpl(get<DatabaseProviderPort>()) }
    single<ArtistRepository> { ArtistRepositoryImpl(get<DatabaseProviderPort>()) }
    single<ConcertRepository> { ConcertRepositoryImpl(get<DatabaseProviderPort>()) }
    single<DateProvider> { DefaultDateProvider() }
    single<SyncRepository> { SyncRepositoryImpl(get(), get<DatabaseProviderPort>(), dateProvider = get()) }
    viewModel { SyncViewModel(get()) }
    viewModel { ConcertosViewModel(get(), get<DatabaseProviderPort>()) }
    viewModel { ArtistasViewModel(get(), get<DatabaseProviderPort>()) }
    viewModel { SalasViewModel(get(), get<DatabaseProviderPort>()) }
    viewModel { MapaViewModel(get(), get(), get<DatabaseProviderPort>()) }
}

expect fun platformModule(): Module
