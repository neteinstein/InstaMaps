package org.neteinstein.instamaps.feature.geocoding.di

import com.google.android.libraries.places.api.Places
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.geocoding.data.PlacesSdkPlaceSearchRepository
import org.neteinstein.instamaps.feature.geocoding.domain.PlaceSearchRepository
import org.neteinstein.instamaps.feature.geocoding.domain.SearchPlaceUseCase

/**
 * Built with the Places API key rather than reading `BuildConfig` directly, so this module stays
 * decoupled from `:app`'s build configuration - the composition root (`:app`) supplies the key.
 */
fun geocodingModule(placesApiKey: String): Module =
    module {
        single {
            val context = androidContext()
            if (!Places.isInitialized()) {
                Places.initializeWithNewPlacesApiEnabled(context, placesApiKey)
            }
            Places.createClient(context)
        }
        singleOf(::PlacesSdkPlaceSearchRepository) { bind<PlaceSearchRepository>() }
        factory { SearchPlaceUseCase(get()) }
    }
