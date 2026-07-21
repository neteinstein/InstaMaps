package org.neteinstein.instamaps.feature.permissions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.neteinstein.instamaps.core.settings.domain.IsGeminiApiKeyConfiguredUseCase

/**
 * Backs [rememberAppReadiness] with the one piece of readiness state that isn't already a
 * Compose-native permission check: whether a Gemini API key is saved. Mirrors the
 * `ShareViewModel.hasGeminiApiKey` pattern it replaces - `null` while the first read from
 * Settings is still in flight (kept distinct from `false` so the Permissions screen doesn't
 * flash a "not added" status before the real value loads), collected eagerly so the value is
 * already current by the time the user comes back from the Settings screen.
 */
class PermissionsViewModel(
    private val isGeminiApiKeyConfiguredUseCase: IsGeminiApiKeyConfiguredUseCase,
) : ViewModel() {
    val hasGeminiApiKey: StateFlow<Boolean?> =
        isGeminiApiKeyConfiguredUseCase()
            .map { hasKey -> hasKey as Boolean? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}
