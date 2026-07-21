package org.neteinstein.instamaps.feature.share.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.neteinstein.instamaps.core.instagramauth.domain.ObserveInstagramAuthStateUseCase
import org.neteinstein.instamaps.core.settings.domain.IsGeminiApiKeyConfiguredUseCase
import org.neteinstein.instamaps.feature.share.domain.ParseSharedTextUseCase
import org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker
import org.neteinstein.instamaps.feature.share.work.toResolvedLocations

/**
 * Drives [ShareUiState] for the share flow. The actual download/OCR/geocode pipeline runs inside
 * [ProcessSharedUrlWorker], not here - this ViewModel only enqueues that work and observes it by
 * its request id via [WorkManager.getWorkInfoByIdFlow]. This split means the pipeline keeps
 * running (and the completion notification still fires) even if the process backgrounding the
 * user just did to share a video kills this ViewModel entirely.
 */
class ShareViewModel(
    private val context: Context,
    private val parseSharedTextUseCase: ParseSharedTextUseCase,
    private val isGeminiApiKeyConfiguredUseCase: IsGeminiApiKeyConfiguredUseCase,
    private val observeInstagramAuthStateUseCase: ObserveInstagramAuthStateUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    /**
     * `null` while the first read from Settings is still in flight - kept distinct from `false`
     * so the main screen doesn't flash a "missing key" warning before the real value loads, and
     * so a shared video isn't allowed to start processing on that same false premise. Collected
     * eagerly (not just while the UI observes it) so the value is already current by the time the
     * user comes back from the Settings screen.
     */
    val hasGeminiApiKey: StateFlow<Boolean?> =
        isGeminiApiKeyConfiguredUseCase()
            .map { hasKey -> hasKey as Boolean? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /**
     * `null`/`false` distinction mirrors [hasGeminiApiKey]. Collected eagerly so
     * [retryIfAuthRequiredPending] fires the moment the user logs back in via
     * `feature:instagramauth`, even though [ShareRoute] isn't composed (and therefore isn't
     * observing anything) while that login screen is on top.
     */
    val isInstagramAuthenticated: StateFlow<Boolean?> =
        observeInstagramAuthStateUseCase()
            .map { isAuthenticated -> isAuthenticated as Boolean? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private var observerJob: Job? = null

    init {
        // ProcessSharedUrlWorker already cleared the stale session by the time uiState becomes
        // AuthRequired (see its toFailureWorkData) - the moment a fresh one is saved, resume the
        // exact URL that failed instead of leaving the user to re-share the same video by hand.
        isInstagramAuthenticated
            .filter { isAuthenticated -> isAuthenticated == true }
            .onEach { retryIfAuthRequiredPending() }
            .launchIn(viewModelScope)
    }

    fun onSharedTextReceived(sharedText: String) {
        val parsed = parseSharedTextUseCase(sharedText)
        if (parsed == null) {
            _uiState.value = ShareUiState.Error("No video link was found in the shared text")
            return
        }
        enqueueProcessing(parsed.url)
    }

    /** Dismisses [ShareUiState.AuthRequired] without logging in, returning to the main screen. */
    fun dismissAuthRequired() {
        if (_uiState.value is ShareUiState.AuthRequired) {
            _uiState.value = ShareUiState.Idle
        }
    }

    private fun retryIfAuthRequiredPending() {
        val pending = _uiState.value
        if (pending is ShareUiState.AuthRequired) {
            enqueueProcessing(pending.url)
        }
    }

    /** Re-runs the pipeline for [url] - the manual counterpart to [retryIfAuthRequiredPending],
     * wired to the Retry button [ShareScreen] shows on [ShareUiState.NotFound]/[ShareUiState.Error]. */
    fun retry(url: String) {
        enqueueProcessing(url)
    }

    // A new share always wins over whatever the UI was showing for a previous one - each
    // share still runs to completion as its own independent Worker (see
    // ProcessSharedUrlWorker.enqueue), only the UI's attention moves to the latest request.
    private fun enqueueProcessing(url: String) {
        _uiState.value = ShareUiState.Processing(ProcessingStage.CHECKING_DESCRIPTION)
        val request = ProcessSharedUrlWorker.enqueue(context, url)

        observerJob?.cancel()
        observerJob =
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(request.id)
                .filterNotNull()
                .onEach { workInfo -> _uiState.value = workInfo.toUiState(url) }
                .launchIn(viewModelScope)
    }

    // [url] comes from the enclosing enqueueProcessing call, not WorkInfo/outputData - it's the
    // one piece every terminal state below needs in common to support a "retry" action, and it's
    // already known here regardless of how the Worker reports its result.
    private fun WorkInfo.toUiState(url: String): ShareUiState =
        when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                ShareUiState.Processing(progress.toProcessingStage())
            WorkInfo.State.SUCCEEDED -> outputData.toFoundUiState(url)
            WorkInfo.State.FAILED -> outputData.toFailedUiState(url)
            WorkInfo.State.CANCELLED -> ShareUiState.Error(message = "Processing was cancelled", url = url)
        }

    private fun Data.toProcessingStage(): ProcessingStage =
        when (getString(ProcessSharedUrlWorker.KEY_STAGE)) {
            ProcessSharedUrlWorker.STAGE_DOWNLOADING -> ProcessingStage.DOWNLOADING
            ProcessSharedUrlWorker.STAGE_EXTRACTING_FRAMES -> ProcessingStage.EXTRACTING_FRAMES
            ProcessSharedUrlWorker.STAGE_ANALYZING_FRAME -> ProcessingStage.ANALYZING_FRAME
            ProcessSharedUrlWorker.STAGE_GEOCODING -> ProcessingStage.GEOCODING
            else -> ProcessingStage.CHECKING_DESCRIPTION
        }

    private fun Data.toFoundUiState(url: String): ShareUiState {
        val locationsJson = getString(ProcessSharedUrlWorker.KEY_LOCATIONS_JSON)
        val locations = locationsJson?.let { runCatching { it.toResolvedLocations() }.getOrNull() }
        if (locations.isNullOrEmpty()) {
            return ShareUiState.Error(message = "Processing finished with an unexpected result", url = url)
        }
        return ShareUiState.Found(locations = locations)
    }

    private fun Data.toFailedUiState(url: String): ShareUiState {
        val message = getString(ProcessSharedUrlWorker.KEY_ERROR_MESSAGE) ?: "Something went wrong"
        val authUrl = getString(ProcessSharedUrlWorker.KEY_URL)
        if (getBoolean(ProcessSharedUrlWorker.KEY_AUTH_REQUIRED, false) && authUrl != null) {
            return ShareUiState.AuthRequired(message = message, url = authUrl)
        }
        val notFound = getBoolean(ProcessSharedUrlWorker.KEY_NOT_FOUND, false)
        return if (notFound) {
            ShareUiState.NotFound(message = message, url = url)
        } else {
            ShareUiState.Error(message = message, url = url)
        }
    }
}
