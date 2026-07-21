package org.neteinstein.instamaps.feature.settings.presentation

/**
 * UI state for [SettingsViewModel]. [apiKeyInput] mirrors the text field as the user types, while
 * [savedApiKey] is the last value known to be persisted (set on initial load and again right after
 * a save) - see [SettingsViewModel] for why the load is a one-shot `.first()` rather than an
 * ongoing collector. [hasUnsavedChanges] compares the two so the Save button can stay disabled
 * until the user actually edits the field. [validationStatus] tracks the outcome of checking
 * [apiKeyInput] against the real Gemini API right after a save - see [ApiKeyValidationStatus].
 */
data class SettingsUiState(
    val apiKeyInput: String = "",
    val savedApiKey: String = "",
    val validationStatus: ApiKeyValidationStatus = ApiKeyValidationStatus.IDLE,
) {
    val hasUnsavedChanges: Boolean get() = apiKeyInput != savedApiKey
}

/**
 * Outcome of checking a just-saved Gemini API key against the real Gemini API (see
 * `core:settings`'s `ValidateGeminiApiKeyUseCase`) - drives the Save button's spinner/color and
 * the supporting text under the field on [SettingsScreen]. [UNKNOWN] is deliberately distinct
 * from [IDLE]: it means a check was attempted but came back inconclusive (e.g. no network), so
 * the UI falls back to neutral styling instead of claiming the key is definitely valid or
 * invalid when it doesn't actually know.
 */
enum class ApiKeyValidationStatus {
    IDLE,
    VALIDATING,
    VALID,
    INVALID,
    UNKNOWN,
}
