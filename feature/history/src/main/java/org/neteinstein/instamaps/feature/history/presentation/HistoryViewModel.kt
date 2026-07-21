package org.neteinstein.instamaps.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.neteinstein.instamaps.core.history.domain.ObserveHistoryUseCase

/**
 * Continuously observes the history list (not a one-shot load) - a share finishing in the
 * background while this screen happens to be open should update it live, the same way the
 * notification would have.
 */
class HistoryViewModel(
    observeHistoryUseCase: ObserveHistoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeHistoryUseCase().collect { entries ->
                _uiState.value = HistoryUiState(entries = entries, isLoading = false)
            }
        }
    }
}
