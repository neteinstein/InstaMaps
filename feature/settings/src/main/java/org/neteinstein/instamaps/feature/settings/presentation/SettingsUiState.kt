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
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
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

/**
 * Drives the "Update to latest" button and its status text on the Settings screen - see
 * [SettingsViewModel.onUpdateClicked].
 */
sealed class UpdateStatus {
    /** Nothing in flight - the button's normal resting state. */
    data object Idle : UpdateStatus()

    data object Checking : UpdateStatus()

    /** The installed build is already the latest one published on GitHub Releases. */
    data class UpToDate(val currentVersionName: String) : UpdateStatus()

    data object Downloading : UpdateStatus()

    /**
     * A newer release exists, but the OS won't let InstaMaps install it yet - see
     * `AppUpdateInstaller.canInstallPackages`. [SettingsScreen] shows a warning banner whose
     * action opens the system "install unknown apps" page for this app
     * ([SettingsViewModel.onEnableSideloadingClicked]); the user is expected to tap
     * "Update to latest" again afterwards, which re-checks and proceeds automatically now that
     * the OS allows it - there's no lifecycle-based auto-recheck of this state.
     */
    data object SideloadingBlocked : UpdateStatus()

    data class Failed(val message: String) : UpdateStatus()
}
