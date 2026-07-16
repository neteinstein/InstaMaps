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
)
