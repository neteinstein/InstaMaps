package org.neteinstein.instamaps.core.instagramauth.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.neteinstein.instamaps.core.instagramauth.data.AndroidKeystoreInstagramSessionCipher
import org.neteinstein.instamaps.core.instagramauth.data.EncryptedInstagramAuthRepository
import org.neteinstein.instamaps.core.instagramauth.data.InstagramSessionCipher
import org.neteinstein.instamaps.core.instagramauth.domain.ClearInstagramSessionUseCase
import org.neteinstein.instamaps.core.instagramauth.domain.InstagramAuthRepository
import org.neteinstein.instamaps.core.instagramauth.domain.ObserveInstagramAuthStateUseCase
import org.neteinstein.instamaps.core.instagramauth.domain.SaveInstagramSessionUseCase

private const val INSTAGRAM_AUTH_DATASTORE_FILE_NAME = "instagram_auth"

/**
 * Wires the DataStore-backed [InstagramAuthRepository] and its use cases. Follows the explicit-
 * constructor convention established by `SettingsModule` for classes that need a
 * [android.content.Context]: built with `single { }` + `androidContext()` rather than
 * `singleOf`. The DataStore file name here must match the exclusion entries in `app`'s
 * `data_extraction_rules.xml`/`full_backup_content.xml` - see `EncryptedInstagramAuthRepository`.
 *
 * The `DataStore<Preferences>` single is qualified with [named] ([INSTAGRAM_AUTH_DATASTORE_FILE_NAME])
 * - see `SettingsModule`'s doc for why: without a qualifier, this would silently collide with
 * every other module's own `DataStore<Preferences>` single (Koin keeps only the last-loaded one
 * reachable via an unqualified `get()`), which for this module specifically would mean the
 * encrypted Instagram session cookie could end up persisted in whichever *other* module's
 * DataStore file won that race - defeating the backup-exclusion this file name is designed for.
 */
val instagramAuthModule =
    module {
        single(named(INSTAGRAM_AUTH_DATASTORE_FILE_NAME)) {
            PreferenceDataStoreFactory.create(
                produceFile = { androidContext().preferencesDataStoreFile(INSTAGRAM_AUTH_DATASTORE_FILE_NAME) },
            )
        }
        single<InstagramSessionCipher> { AndroidKeystoreInstagramSessionCipher() }
        single<InstagramAuthRepository> {
            EncryptedInstagramAuthRepository(
                dataStore = get(named(INSTAGRAM_AUTH_DATASTORE_FILE_NAME)),
                cipher = get(),
            )
        }
        single { ObserveInstagramAuthStateUseCase(repository = get()) }
        single { SaveInstagramSessionUseCase(repository = get()) }
        single { ClearInstagramSessionUseCase(repository = get()) }
    }
