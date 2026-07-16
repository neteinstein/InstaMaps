package org.neteinstein.instamaps.feature.maps.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.BuildMapsDeepLinkUseCase

val mapsModule =
    module {
        factory { BuildMapsDeepLinkUseCase() }
        single { MapsLauncher(androidContext(), get()) }
    }
