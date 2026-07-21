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
import org.neteinstein.instamaps.core.update.AppUpdateInstaller
import org.neteinstein.instamaps.core.update.domain.AppUpdate
import org.neteinstein.instamaps.core.update.domain.CheckForUpdateUseCase
import org.neteinstein.instamaps.core.update.domain.DownloadAppUpdateUseCase
import org.neteinstein.instamaps.core.update.domain.UpdateCheckResult

/**
 * Loads the currently saved Gemini API key once on init - a one-shot `.first()`, not an ongoing
 * collector - so nothing can clobber the text the user is actively typing in the field while
 * this screen is open.
 */
class SettingsViewModel(
    private val observeGeminiApiKeyUseCase: ObserveGeminiApiKeyUseCase,
    private val saveGeminiApiKeyUseCase: SaveGeminiApiKeyUseCase,
    private val validateGeminiApiKeyUseCase: ValidateGeminiApiKeyUseCase,
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val downloadAppUpdateUseCase: DownloadAppUpdateUseCase,
    private val appUpdateInstaller: AppUpdateInstaller,
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

    /**
     * Checks GitHub Releases and, if a newer build exists, downloads it and launches the system
     * installer - unless [AppUpdateInstaller.canInstallPackages] says the OS will block that
     * install outright, in which case this stops at [UpdateStatus.SideloadingBlocked] without
     * downloading anything (no point spending the bandwidth on an APK the system won't let
     * through). [UpdateStatus.Checking] and [UpdateStatus.Downloading] both keep the screen from
     * timing out for as long as they're the current status - see `SettingsScreen`'s
     * `KeepScreenOnWhile` - since this whole flow runs on a plain `viewModelScope` coroutine, not
     * a `WorkManager` job that would survive the app leaving the foreground.
     */
    fun onUpdateClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Checking)
            checkForUpdateUseCase()
                .onSuccess { result -> handleCheckResult(result) }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Failed(error.toUserMessage()))
                }
        }
    }

    /** Deep-links to the system "install unknown apps" settings page for this app. */
    fun onEnableSideloadingClicked() {
        appUpdateInstaller.openInstallPermissionSettings()
    }

    private suspend fun handleCheckResult(result: UpdateCheckResult) {
        when (result) {
            is UpdateCheckResult.UpToDate ->
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.UpToDate(result.currentVersionName))
            is UpdateCheckResult.UpdateAvailable -> downloadAndInstall(result.update)
        }
    }

    private suspend fun downloadAndInstall(update: AppUpdate) {
        if (!appUpdateInstaller.canInstallPackages()) {
            _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.SideloadingBlocked)
            return
        }

        _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Downloading)
        downloadAppUpdateUseCase(update)
            .onSuccess { apkFile ->
                // Fire the installer intent while updateStatus is still Downloading (not Idle)
                // so SettingsScreen's keep-screen-on effect - tied to Checking/Downloading - is
                // still active for this exact call: that intent is a foreground-only activity
                // start, silently dropped by the OS if the screen has already locked by now.
                appUpdateInstaller.installPackage(apkFile)
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Idle)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Failed(error.toUserMessage()))
            }
    }

    private fun Throwable.toUserMessage(): String = message ?: "Something went wrong"
}
