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
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val downloadAppUpdateUseCase: DownloadAppUpdateUseCase,
    private val appUpdateInstaller: AppUpdateInstaller,
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

    /**
     * Checks GitHub Releases and, if a newer build exists, downloads it and launches the system
     * installer - unless [AppUpdateInstaller.canInstallPackages] says the OS will block that
     * install outright, in which case this stops at [UpdateStatus.SideloadingBlocked] without
     * downloading anything (no point spending the bandwidth on an APK the system won't let
     * through).
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
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Idle)
                appUpdateInstaller.installPackage(apkFile)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(updateStatus = UpdateStatus.Failed(error.toUserMessage()))
            }
    }

    private fun Throwable.toUserMessage(): String = message ?: "Something went wrong"
}
