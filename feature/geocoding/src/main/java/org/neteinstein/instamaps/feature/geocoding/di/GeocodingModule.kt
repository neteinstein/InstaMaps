package org.neteinstein.instamaps.feature.geocoding.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.geocoding.data.PlacesSdkPlaceSearchRepository
import org.neteinstein.instamaps.feature.geocoding.domain.PlaceSearchRepository
import org.neteinstein.instamaps.feature.geocoding.domain.SearchPlaceUseCase

/**
 * No longer takes a Places API key constructor parameter: [PlacesSdkPlaceSearchRepository] reads
 * the current key from `core:settings` lazily on every search instead of a Koin-startup
 * snapshot, so a key saved on the Settings screen takes effect without restarting the app.
 */
val geocodingModule =
    module {
        single<PlaceSearchRepository> {
            PlacesSdkPlaceSearchRepository(context = androidContext(), settingsRepository = get())
        }
        factory { SearchPlaceUseCase(get()) }
    }
