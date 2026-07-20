package org.neteinstein.instamaps.core.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * Persists user-editable app settings - currently just the Gemini API key entered on the
 * Settings screen (see `feature:settings`). Backed by DataStore Preferences, stored under the
 * app's regular files directory so it is included in Android's default Auto Backup (enabled by
 * `android:allowBackup="true"` in the manifest) and restored automatically on other devices.
 */
interface AppSettingsRepository {
    /** Emits the current key, or `null` if none has been saved (or it is blank). */
    fun observeGeminiApiKey(): Flow<String?>

    /** Trims [apiKey] before storing; an empty result clears any previously saved key. */
    suspend fun saveGeminiApiKey(apiKey: String)
}
