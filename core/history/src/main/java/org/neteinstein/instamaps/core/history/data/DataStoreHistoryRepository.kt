package org.neteinstein.instamaps.core.history.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.neteinstein.instamaps.core.history.domain.HistoryEntry
import org.neteinstein.instamaps.core.history.domain.HistoryLocation
import org.neteinstein.instamaps.core.history.domain.HistoryRepository
import org.neteinstein.instamaps.core.history.domain.MAX_HISTORY_ENTRIES
import java.io.IOException
import java.util.UUID

/**
 * [HistoryRepository] backed by Jetpack DataStore Preferences - the whole list is (de)serialized
 * as one JSON array under a single string key, the same trick `feature:share`'s
 * `ResolvedLocationsJson.kt` uses to fit a `List<...>` into a primitive-only storage API. The
 * [DataStore] file lives under the app's regular files directory (see `HistoryModule`'s
 * `preferencesDataStoreFile` call), so a shared video's history entry is covered by the app's
 * default Auto Backup, same as `core:settings`'s API key.
 */
class DataStoreHistoryRepository(
    private val dataStore: DataStore<Preferences>,
) : HistoryRepository {
    override fun observeEntries(): Flow<List<HistoryEntry>> =
        dataStore.data
            // Same defensive treatment as DataStoreAppSettingsRepository: a corrupted/unreadable
            // file is treated as "no history yet" rather than crashing the collector.
            .catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .map { preferences -> preferences[HISTORY_ENTRIES_JSON]?.toHistoryEntries().orEmpty() }

    override suspend fun recordEntry(
        url: String,
        locations: List<HistoryLocation>,
    ) {
        val entry =
            HistoryEntry(
                id = UUID.randomUUID().toString(),
                url = url,
                timestamp = System.currentTimeMillis(),
                locations = locations,
            )
        dataStore.edit { preferences ->
            val existing = preferences[HISTORY_ENTRIES_JSON]?.toHistoryEntries().orEmpty()
            preferences[HISTORY_ENTRIES_JSON] = (listOf(entry) + existing).take(MAX_HISTORY_ENTRIES).toJson()
        }
    }

    private companion object {
        val HISTORY_ENTRIES_JSON: Preferences.Key<String> = stringPreferencesKey("history_entries_json")
    }
}

private fun List<HistoryEntry>.toJson(): String {
    val array = JSONArray()
    forEach { entry ->
        array.put(
            JSONObject().apply {
                put("id", entry.id)
                put("url", entry.url)
                put("timestamp", entry.timestamp)
                put(
                    "locations",
                    JSONArray().apply {
                        entry.locations.forEach { location ->
                            put(
                                JSONObject().apply {
                                    put("name", location.name)
                                    put("address", location.address ?: JSONObject.NULL)
                                },
                            )
                        }
                    },
                )
            },
        )
    }
    return array.toString()
}

/** Malformed/corrupted JSON is treated the same as "no history yet" rather than crashing. */
private fun String.toHistoryEntries(): List<HistoryEntry> =
    try {
        val array = JSONArray(this)
        (0 until array.length()).map { index -> array.getJSONObject(index).toHistoryEntry() }
    } catch (_: JSONException) {
        emptyList()
    }

private fun JSONObject.toHistoryEntry(): HistoryEntry {
    val locationsArray = optJSONArray("locations") ?: JSONArray()
    return HistoryEntry(
        id = getString("id"),
        url = getString("url"),
        timestamp = getLong("timestamp"),
        locations =
            (0 until locationsArray.length()).map { index ->
                val item = locationsArray.getJSONObject(index)
                HistoryLocation(name = item.getString("name"), address = item.optStringOrNull("address"))
            },
    )
}

private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
