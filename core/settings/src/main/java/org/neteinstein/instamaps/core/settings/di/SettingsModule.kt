package org.neteinstein.instamaps.core.settings.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.neteinstein.instamaps.core.settings.data.DataStoreAppSettingsRepository
import org.neteinstein.instamaps.core.settings.data.HttpGeminiApiKeyValidator
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import org.neteinstein.instamaps.core.settings.domain.GeminiApiKeyValidator
import org.neteinstein.instamaps.core.settings.domain.IsGeminiApiKeyConfiguredUseCase
import org.neteinstein.instamaps.core.settings.domain.ObserveGeminiApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.SaveGeminiApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.ValidateGeminiApiKeyUseCase

private const val SETTINGS_DATASTORE_FILE_NAME = "app_settings"

/**
 * Wires the DataStore-backed [AppSettingsRepository] and its use cases. Follows the explicit-
 * constructor convention established by `feature:videoprocessing`/`feature:maps` for classes
 * that need a [android.content.Context]: built with `single { }` + `androidContext()` rather
 * than `singleOf`.
 *
 * The `DataStore<Preferences>` single is qualified with [named] ([SETTINGS_DATASTORE_FILE_NAME]) -
 * Koin indexes `single { }` definitions by type alone when unqualified, and type-erasure means
 * every module in this app that stores its own `DataStore<Preferences>` (this one,
 * `core:instagramauth`'s, `core:history`'s) would otherwise collide: Koin silently keeps only the
 * *last*-loaded module's definition reachable via an unqualified `get()`, with no error, so every
 * repository would end up sharing whichever single won that race instead of its own file.
 */
val settingsModule =
    module {
        single(named(SETTINGS_DATASTORE_FILE_NAME)) {
            PreferenceDataStoreFactory.create(
                produceFile = { androidContext().preferencesDataStoreFile(SETTINGS_DATASTORE_FILE_NAME) },
            )
        }
        single<AppSettingsRepository> {
            DataStoreAppSettingsRepository(dataStore = get(named(SETTINGS_DATASTORE_FILE_NAME)))
        }
        single { ObserveGeminiApiKeyUseCase(repository = get()) }
        single { SaveGeminiApiKeyUseCase(repository = get()) }
        single { IsGeminiApiKeyConfiguredUseCase(repository = get()) }
        single<GeminiApiKeyValidator> { HttpGeminiApiKeyValidator(dispatcherProvider = get()) }
        single { ValidateGeminiApiKeyUseCase(validator = get()) }
    }
