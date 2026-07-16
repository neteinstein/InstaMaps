package org.neteinstein.instamaps.core.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import java.io.IOException

/**
 * [AppSettingsRepository] backed by Jetpack DataStore Preferences. The [DataStore] file lives
 * under the app's regular files directory (see `SettingsModule`'s `preferencesDataStoreFile`
 * call) rather than `noBackupFilesDir`/`cacheDir`, so it is swept up by Android's default Auto
 * Backup - the same `android:allowBackup="true"` the app already declares - letting a saved key
 * reappear after a restore on a new device without any extra backup-agent code.
 */
class DataStoreAppSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppSettingsRepository {
    override fun observePlacesApiKey(): Flow<String?> =
        dataStore.data
            // DataStore's `data` Flow can throw on a corrupted/unreadable file - treat that the
            // same as "nothing saved yet" rather than crashing the collector (the main screen's
            // warning banner, and the Settings screen's prefill).
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map { preferences -> preferences[PLACES_API_KEY]?.takeIf { it.isNotBlank() } }

    override suspend fun savePlacesApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        dataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(PLACES_API_KEY)
            } else {
                preferences[PLACES_API_KEY] = trimmed
            }
        }
    }

    private companion object {
        val PLACES_API_KEY: Preferences.Key<String> = stringPreferencesKey("places_api_key")
    }
}
