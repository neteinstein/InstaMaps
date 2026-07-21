package org.neteinstein.instamaps.core.history.domain

import kotlinx.coroutines.flow.Flow

/**
 * Observes the "last shared videos" list for `feature:history`'s history screen, so it doesn't
 * need to depend on [HistoryRepository] directly.
 */
class ObserveHistoryUseCase(
    private val repository: HistoryRepository,
) {
    operator fun invoke(): Flow<List<HistoryEntry>> = repository.observeEntries()
}
