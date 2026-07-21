package org.neteinstein.instamaps.core.history.domain

import kotlinx.coroutines.flow.Flow

/** The history list never grows past this many entries - the oldest ones are dropped first. */
const val MAX_HISTORY_ENTRIES = 50

/**
 * Persists the most recently shared videos and what (if anything) was found for them, newest
 * first, capped at [MAX_HISTORY_ENTRIES] - see `feature:history`'s history screen. Backed by
 * DataStore Preferences, the same approach as `core:settings`'s `AppSettingsRepository`.
 */
interface HistoryRepository {
    /** Emits the current list, newest entry first, whenever it changes. */
    fun observeEntries(): Flow<List<HistoryEntry>>

    /**
     * Appends a new entry for [url] at the front of the list, trimming it back down to
     * [MAX_HISTORY_ENTRIES] if needed. [locations] is empty for a `NotFound`/`Failed` outcome.
     */
    suspend fun recordEntry(
        url: String,
        locations: List<HistoryLocation> = emptyList(),
    )
}
