package org.neteinstein.instamaps.core.history.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.neteinstein.instamaps.core.history.data.DataStoreHistoryRepository
import org.neteinstein.instamaps.core.history.domain.HistoryRepository
import org.neteinstein.instamaps.core.history.domain.ObserveHistoryUseCase
import org.neteinstein.instamaps.core.history.domain.RecordHistoryEntryUseCase

private const val HISTORY_DATASTORE_FILE_NAME = "share_history"

/**
 * Wires the DataStore-backed [HistoryRepository] and its use cases - mirrors `SettingsModule`.
 * The `DataStore<Preferences>` single is qualified with [named] ([HISTORY_DATASTORE_FILE_NAME]) -
 * see `SettingsModule`'s doc: an unqualified `single { }` here would silently collide with every
 * other module's own `DataStore<Preferences>` single once all modules are loaded together in
 * `InstaMapsApplication`.
 */
val historyModule =
    module {
        single(named(HISTORY_DATASTORE_FILE_NAME)) {
            PreferenceDataStoreFactory.create(
                produceFile = { androidContext().preferencesDataStoreFile(HISTORY_DATASTORE_FILE_NAME) },
            )
        }
        single<HistoryRepository> {
            DataStoreHistoryRepository(dataStore = get(named(HISTORY_DATASTORE_FILE_NAME)))
        }
        single { ObserveHistoryUseCase(repository = get()) }
        single { RecordHistoryEntryUseCase(repository = get()) }
    }
