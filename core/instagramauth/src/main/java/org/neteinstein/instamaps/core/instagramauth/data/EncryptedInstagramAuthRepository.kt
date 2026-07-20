package org.neteinstein.instamaps.core.instagramauth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.neteinstein.instamaps.core.instagramauth.domain.InstagramAuthRepository
import java.io.IOException

/**
 * [InstagramAuthRepository] backed by Jetpack DataStore Preferences, encrypting the cookie with
 * [cipher] before it's ever written to disk (see [AndroidKeystoreInstagramSessionCipher] for the
 * real implementation wired in `InstagramAuthModule`). Unlike `core:settings`'s
 * `DataStoreAppSettingsRepository`, this module's DataStore file is deliberately *excluded* from
 * Android's Auto Backup (see `app`'s `data_extraction_rules.xml`/`full_backup_content.xml`) since
 * the Keystore-backed decryption key it depends on never survives a restore to a new device - a
 * restored-but-undecryptable blob would otherwise strand [observeIsAuthenticated] reporting
 * `true` for a session that can never actually be used.
 */
class EncryptedInstagramAuthRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: InstagramSessionCipher,
) : InstagramAuthRepository {
    override fun observeIsAuthenticated(): Flow<Boolean> =
        safeData.map { preferences -> preferences[SESSION_COOKIE]?.let(cipher::decrypt) != null }

    override suspend fun getCookieHeader(): String? {
        val encrypted = safeData.map { it[SESSION_COOKIE] }.first() ?: return null
        val decrypted = cipher.decrypt(encrypted)
        // The Keystore key can outlive its usefulness (e.g. restored to a new device without the
        // original key, or the ciphertext is otherwise corrupt) - self-heal by clearing rather
        // than repeatedly failing every download with the same dead cookie.
        if (decrypted == null) clearSession()
        return decrypted
    }

    override suspend fun saveSession(cookieHeader: String) {
        val trimmed = cookieHeader.trim()
        if (trimmed.isEmpty()) {
            clearSession()
            return
        }
        val encrypted = cipher.encrypt(trimmed)
        dataStore.edit { preferences -> preferences[SESSION_COOKIE] = encrypted }
    }

    override suspend fun clearSession() {
        dataStore.edit { preferences -> preferences.remove(SESSION_COOKIE) }
    }

    // DataStore's `data` Flow can throw on a corrupted/unreadable file - treat that the same as
    // "nothing saved yet" rather than crashing the collector, mirroring
    // `DataStoreAppSettingsRepository`.
    private val safeData: Flow<Preferences>
        get() =
            dataStore.data.catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }

    private companion object {
        val SESSION_COOKIE: Preferences.Key<String> = stringPreferencesKey("session_cookie")
    }
}
