package org.neteinstein.instamaps.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.neteinstein.instamaps.core.settings.domain.ObserveGeminiApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.SaveGeminiApiKeyUseCase

/**
 * Loads the currently saved Gemini API key once on init - a one-shot `.first()`, not an ongoing
 * collector - so nothing can clobber the text the user is actively typing in the field while
 * this screen is open.
 */
class SettingsViewModel(
    private val observeGeminiApiKeyUseCase: ObserveGeminiApiKeyUseCase,
    private val saveGeminiApiKeyUseCase: SaveGeminiApiKeyUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedKey = observeGeminiApiKeyUseCase().first()
            _uiState.value = _uiState.value.copy(apiKeyInput = savedKey.orEmpty())
        }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.value = _uiState.value.copy(apiKeyInput = value, justSaved = false)
    }

    fun onSaveClicked() {
        viewModelScope.launch {
            saveGeminiApiKeyUseCase(_uiState.value.apiKeyInput)
            _uiState.value = _uiState.value.copy(justSaved = true)
        }
    }
}
