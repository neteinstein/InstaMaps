package org.neteinstein.instamaps.feature.share.presentation

import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation

/**
 * UI state for [ShareViewModel]. Mirrors `ProcessSharedUrlWorker`'s reported stages/result rather
 * than `ShareProcessingProgress` directly, since the ViewModel rebuilds this from `WorkInfo`
 * (progress `Data` + terminal state), not from a live `Flow` it's collecting itself - see
 * [ShareViewModel] for why the pipeline runs inside a `CoroutineWorker` instead.
 */
sealed class ShareUiState {
    data object Idle : ShareUiState()

    data class Processing(val stage: ProcessingStage) : ShareUiState()

    /** [locations] is never empty - ordered most-to-least likely to be the video's real subject. */
    data class Found(val locations: List<ResolvedLocation>) : ShareUiState()

    /** [url] is the video that was being processed - carried so the UI can offer a retry button. */
    data class NotFound(val message: String, val url: String) : ShareUiState()

    /**
     * yt-dlp reported that Instagram is demanding a (re-)login for [url] - see
     * `YtDlpVideoDownloadRepository`'s `ytDlpErrorToAppError`. [ShareViewModel] already cleared
     * the stale session by the time this is emitted, and automatically retries [url] once
     * [ShareViewModel.isInstagramAuthenticated] next reports `true`, so the UI only needs to get
     * the user to [org.neteinstein.instamaps.feature.share.presentation.ShareRoute]'s
     * `onNeedsInstagramLogin` callback - no manual "retry" action required.
     */
    data class AuthRequired(val message: String, val url: String) : ShareUiState()

    /**
     * [url] is the video that was being processed when this failure happened, so the UI can offer
     * a retry button that resumes the exact same video via [ShareViewModel.retry] - `null` only
     * when there was no resolved video to retry in the first place (e.g. the shared text itself
     * didn't contain a recognizable video link).
     */
    data class Error(val message: String, val url: String? = null) : ShareUiState()
}

enum class ProcessingStage {
    CHECKING_DESCRIPTION,
    DOWNLOADING,
    EXTRACTING_FRAMES,
    ANALYZING_FRAME,
    GEOCODING,
}
