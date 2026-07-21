package org.neteinstein.instamaps.core.history.domain

/**
 * One row in the "last shared videos" history list (see [HistoryRepository]). [locations] is
 * empty when the pipeline finished as `NotFound`/`Failed`/still `Processing` - the history
 * screen still lists the entry, so the user can see and re-open the original video, just without
 * a Maps CTA. When there's more than one location (Gemini can rank several candidates - see
 * `feature:geocoding`'s `ResolvedLocation`), [locations] keeps every one of them in ranked order,
 * mirroring what the in-app results screen showed at the time.
 */
data class HistoryEntry(
    val id: String,
    val url: String,
    val timestamp: Long,
    val locations: List<HistoryLocation> = emptyList(),
)
