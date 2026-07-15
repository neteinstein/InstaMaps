package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Coarse-grained progress emitted by [ExtractLocationCandidatesUseCase] so a ViewModel can show
 * the user what stage a (potentially slow) download+OCR pipeline is in, instead of a single
 * indeterminate spinner for the whole operation.
 */
sealed class VideoAnalysisProgress {
    data object Downloading : VideoAnalysisProgress()

    data object ExtractingFrames : VideoAnalysisProgress()

    data class AnalyzingFrame(val frameIndex: Int) : VideoAnalysisProgress()

    data class Completed(val candidates: List<LocationCandidate>) : VideoAnalysisProgress()

    data class Failed(val error: Throwable) : VideoAnalysisProgress()
}
