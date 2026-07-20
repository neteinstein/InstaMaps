package org.neteinstein.instamaps.feature.geocoding.di

import org.koin.dsl.module
import org.neteinstein.instamaps.feature.geocoding.data.GeminiLocationRepository
import org.neteinstein.instamaps.feature.geocoding.domain.LocationRepository
import org.neteinstein.instamaps.feature.geocoding.domain.ResolveLocationUseCase

val geocodingModule =
    module {
        single<LocationRepository> {
            GeminiLocationRepository(settingsRepository = get(), dispatcherProvider = get())
        }
        factory { ResolveLocationUseCase(get()) }
    }
