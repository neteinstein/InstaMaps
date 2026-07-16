package org.neteinstein.instamaps.feature.share.presentation

import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

/**
 * UI state for [ShareViewModel]. Mirrors `ProcessSharedUrlWorker`'s reported stages/result rather
 * than `ShareProcessingProgress` directly, since the ViewModel rebuilds this from `WorkInfo`
 * (progress `Data` + terminal state), not from a live `Flow` it's collecting itself - see
 * [ShareViewModel] for why the pipeline runs inside a `CoroutineWorker` instead.
 */
sealed class ShareUiState {
    data object Idle : ShareUiState()

    data class Processing(val stage: ProcessingStage) : ShareUiState()

    data class Found(
        val destination: MapsDestination,
        val displayName: String,
    ) : ShareUiState()

    data class NotFound(val message: String) : ShareUiState()

    data class Error(val message: String) : ShareUiState()
}

enum class ProcessingStage {
    CHECKING_DESCRIPTION,
    DOWNLOADING,
    EXTRACTING_FRAMES,
    ANALYZING_FRAME,
    GEOCODING,
}
