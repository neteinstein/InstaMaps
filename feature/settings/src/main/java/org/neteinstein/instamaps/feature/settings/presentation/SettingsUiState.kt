package org.neteinstein.instamaps.feature.settings.presentation

/**
 * UI state for [SettingsViewModel]. [apiKeyInput] mirrors the text field as the user types, so it
 * only reflects the persisted value once on load and again right after a save - not on every
 * keystroke - see [SettingsViewModel] for why the load is a one-shot `.first()` rather than an
 * ongoing collector.
 */
data class SettingsUiState(
    val apiKeyInput: String = "",
    val justSaved: Boolean = false,
    val updateStatus: UpdateStatus = UpdateStatus.Idle,
)

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
