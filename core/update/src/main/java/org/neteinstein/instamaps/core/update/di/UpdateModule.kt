package org.neteinstein.instamaps.core.update.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.neteinstein.instamaps.core.update.AppUpdateInstaller
import org.neteinstein.instamaps.core.update.data.GitHubUpdateRepository
import org.neteinstein.instamaps.core.update.domain.CheckForUpdateUseCase
import org.neteinstein.instamaps.core.update.domain.ClearDownloadedUpdateUseCase
import org.neteinstein.instamaps.core.update.domain.DownloadAppUpdateUseCase
import org.neteinstein.instamaps.core.update.domain.UpdateRepository

/**
 * Wires the GitHub-Releases-backed [UpdateRepository], its use cases, and [AppUpdateInstaller] -
 * mirrors `GeocodingModule`'s shape (single repository + factory use cases). Constructed with
 * `androidContext()` explicitly, matching `MapsModule`/`VideoProcessingModule`'s convention for
 * classes that need a [android.content.Context].
 */
val updateModule =
    module {
        single<UpdateRepository> { GitHubUpdateRepository(context = androidContext(), dispatcherProvider = get()) }
        factory { CheckForUpdateUseCase(repository = get()) }
        factory { DownloadAppUpdateUseCase(repository = get()) }
        factory { ClearDownloadedUpdateUseCase(repository = get()) }
        single { AppUpdateInstaller(androidContext()) }
    }
