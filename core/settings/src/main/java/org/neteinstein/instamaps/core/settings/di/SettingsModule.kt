package org.neteinstein.instamaps.core.settings.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.neteinstein.instamaps.core.settings.data.DataStoreAppSettingsRepository
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import org.neteinstein.instamaps.core.settings.domain.IsPlacesApiKeyConfiguredUseCase
import org.neteinstein.instamaps.core.settings.domain.ObservePlacesApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.SavePlacesApiKeyUseCase

private const val SETTINGS_DATASTORE_FILE_NAME = "app_settings"

/**
 * Wires the DataStore-backed [AppSettingsRepository] and its use cases. Follows the explicit-
 * constructor convention established by `feature:videoprocessing`/`feature:maps` for classes
 * that need a [android.content.Context]: built with `single { }` + `androidContext()` rather
 * than `singleOf`.
 */
val settingsModule =
    module {
        single {
            PreferenceDataStoreFactory.create(
                produceFile = { androidContext().preferencesDataStoreFile(SETTINGS_DATASTORE_FILE_NAME) },
            )
        }
        single<AppSettingsRepository> { DataStoreAppSettingsRepository(dataStore = get()) }
        single { ObservePlacesApiKeyUseCase(repository = get()) }
        single { SavePlacesApiKeyUseCase(repository = get()) }
        single { IsPlacesApiKeyConfiguredUseCase(repository = get()) }
    }
