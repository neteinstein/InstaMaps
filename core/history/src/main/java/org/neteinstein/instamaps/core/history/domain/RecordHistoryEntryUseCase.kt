package org.neteinstein.instamaps.core.history.domain

/**
 * Records one finished (or failed) share to the history list - called from `feature:share`'s
 * `ProcessSharedUrlWorker` on every terminal pipeline outcome, so `feature:history`'s screen has
 * something to show for every share, not just the successful ones.
 */
class RecordHistoryEntryUseCase(
    private val repository: HistoryRepository,
) {
    suspend operator fun invoke(
        url: String,
        locations: List<HistoryLocation> = emptyList(),
    ) {
        repository.recordEntry(url = url, locations = locations)
    }
}
