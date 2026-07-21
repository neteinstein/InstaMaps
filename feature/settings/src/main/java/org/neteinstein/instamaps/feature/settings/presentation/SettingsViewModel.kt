package org.neteinstein.instamaps.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.neteinstein.instamaps.core.settings.domain.ApiKeyValidity
import org.neteinstein.instamaps.core.settings.domain.ObserveGeminiApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.SaveGeminiApiKeyUseCase
import org.neteinstein.instamaps.core.settings.domain.ValidateGeminiApiKeyUseCase

/**
 * Loads the currently saved Gemini API key once on init - a one-shot `.first()`, not an ongoing
 * collector - so nothing can clobber the text the user is actively typing in the field while
 * this screen is open.
 */
class SettingsViewModel(
    private val observeGeminiApiKeyUseCase: ObserveGeminiApiKeyUseCase,
    private val saveGeminiApiKeyUseCase: SaveGeminiApiKeyUseCase,
    private val validateGeminiApiKeyUseCase: ValidateGeminiApiKeyUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedKey = observeGeminiApiKeyUseCase().first().orEmpty()
            _uiState.value = _uiState.value.copy(apiKeyInput = savedKey, savedApiKey = savedKey)
        }
    }

    fun onApiKeyChanged(value: String) {
        _uiState.value =
            _uiState.value.copy(
                apiKeyInput = value,
                validationStatus = ApiKeyValidationStatus.IDLE,
            )
    }

    /**
     * Always persists [SettingsUiState.apiKeyInput] - regardless of what validation below finds,
     * a key the user explicitly chose to save is saved - then checks it against the real Gemini
     * API purely to drive the Save button's loading/green/red feedback. Skips that check
     * entirely for a blank key (clearing a saved key isn't "invalid", there's just nothing to
     * verify), and discards a validation result that comes back after the user has already
     * edited the field again so a slow, now-stale response can't clobber a newer edit's state.
     */
    fun onSaveClicked() {
        val apiKey = _uiState.value.apiKeyInput
        if (apiKey.isBlank()) {
            viewModelScope.launch {
                saveGeminiApiKeyUseCase(apiKey)
                _uiState.value = _uiState.value.copy(savedApiKey = apiKey, validationStatus = ApiKeyValidationStatus.IDLE)
            }
            return
        }

        _uiState.value = _uiState.value.copy(validationStatus = ApiKeyValidationStatus.VALIDATING)
        viewModelScope.launch {
            saveGeminiApiKeyUseCase(apiKey)
            _uiState.value = _uiState.value.copy(savedApiKey = apiKey)

            val validity = validateGeminiApiKeyUseCase(apiKey).getOrNull()
            if (_uiState.value.apiKeyInput == apiKey) {
                _uiState.value =
                    _uiState.value.copy(
                        validationStatus =
                            when (validity) {
                                ApiKeyValidity.VALID -> ApiKeyValidationStatus.VALID
                                ApiKeyValidity.INVALID -> ApiKeyValidationStatus.INVALID
                                null -> ApiKeyValidationStatus.UNKNOWN
                            },
                    )
            }
        }
    }
}
