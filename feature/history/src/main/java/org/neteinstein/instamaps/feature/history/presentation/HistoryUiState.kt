package org.neteinstein.instamaps.feature.history.presentation

import org.neteinstein.instamaps.core.history.domain.HistoryEntry

/**
 * @property isLoading `true` only until the very first value is emitted from
 * `ObserveHistoryUseCase` - lets the screen show a brief loading spinner instead of flashing an
 * empty state while DataStore's first read is still in flight.
 */
data class HistoryUiState(
    val entries: List<HistoryEntry> = emptyList(),
    val isLoading: Boolean = true,
)
