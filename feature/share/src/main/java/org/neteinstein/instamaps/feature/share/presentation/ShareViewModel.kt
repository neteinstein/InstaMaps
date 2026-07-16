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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import org.neteinstein.instamaps.core.settings.domain.IsPlacesApiKeyConfiguredUseCase
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.share.domain.ParseSharedTextUseCase
import org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker

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
    private val isPlacesApiKeyConfiguredUseCase: IsPlacesApiKeyConfiguredUseCase,
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
    val hasPlacesApiKey: StateFlow<Boolean?> =
        isPlacesApiKeyConfiguredUseCase()
            .map { hasKey -> hasKey as Boolean? }
            .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private var observerJob: Job? = null

    fun onSharedTextReceived(sharedText: String) {
        val parsed = parseSharedTextUseCase(sharedText)
        if (parsed == null) {
            _uiState.value = ShareUiState.Error("No video link was found in the shared text")
            return
        }

        _uiState.value = ShareUiState.Processing(ProcessingStage.DOWNLOADING)
        val request = ProcessSharedUrlWorker.enqueue(context, parsed.url)

        // A new share always wins over whatever the UI was showing for a previous one - each
        // share still runs to completion as its own independent Worker (see
        // ProcessSharedUrlWorker.enqueue), only the UI's attention moves to the latest request.
        observerJob?.cancel()
        observerJob =
            WorkManager.getInstance(context)
                .getWorkInfoByIdFlow(request.id)
                .filterNotNull()
                .onEach { workInfo -> _uiState.value = workInfo.toUiState() }
                .launchIn(viewModelScope)
    }

    private fun WorkInfo.toUiState(): ShareUiState =
        when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                ShareUiState.Processing(progress.toProcessingStage())
            WorkInfo.State.SUCCEEDED -> outputData.toFoundUiState()
            WorkInfo.State.FAILED -> outputData.toFailedUiState()
            WorkInfo.State.CANCELLED -> ShareUiState.Error("Processing was cancelled")
        }

    private fun Data.toProcessingStage(): ProcessingStage =
        when (getString(ProcessSharedUrlWorker.KEY_STAGE)) {
            ProcessSharedUrlWorker.STAGE_EXTRACTING_FRAMES -> ProcessingStage.EXTRACTING_FRAMES
            ProcessSharedUrlWorker.STAGE_ANALYZING_FRAME -> ProcessingStage.ANALYZING_FRAME
            ProcessSharedUrlWorker.STAGE_GEOCODING -> ProcessingStage.GEOCODING
            else -> ProcessingStage.DOWNLOADING
        }

    private fun Data.toFoundUiState(): ShareUiState {
        val query = getString(ProcessSharedUrlWorker.KEY_MAPS_QUERY)
        val displayName = getString(ProcessSharedUrlWorker.KEY_DISPLAY_NAME)
        if (query.isNullOrBlank() || displayName == null) {
            return ShareUiState.Error("Processing finished with an unexpected result")
        }
        return ShareUiState.Found(
            destination = MapsDestination(query = query, placeId = getString(ProcessSharedUrlWorker.KEY_PLACE_ID)),
            displayName = displayName,
        )
    }

    private fun Data.toFailedUiState(): ShareUiState {
        val message = getString(ProcessSharedUrlWorker.KEY_ERROR_MESSAGE) ?: "Something went wrong"
        val notFound = getBoolean(ProcessSharedUrlWorker.KEY_NOT_FOUND, false)
        return if (notFound) ShareUiState.NotFound(message) else ShareUiState.Error(message)
    }
}
