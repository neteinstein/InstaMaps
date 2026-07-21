package org.neteinstein.instamaps.feature.share.domain

import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation

/**
 * Progress for the full share-to-maps-link pipeline. Layers geocoding resolution on top of
 * [org.neteinstein.instamaps.feature.videoprocessing.domain.VideoAnalysisProgress] (see
 * [ProcessSharedUrlUseCase]) so the UI can show one continuous story - download, extract, read,
 * search - instead of switching state shapes partway through.
 */
sealed class ShareProcessingProgress {
    data object CheckingDescription : ShareProcessingProgress()

    data object Downloading : ShareProcessingProgress()

    data object ExtractingFrames : ShareProcessingProgress()

    data class AnalyzingFrame(val frameIndex: Int) : ShareProcessingProgress()

    data object Geocoding : ShareProcessingProgress()

    /** [locations] is never empty - ordered most-to-least likely to be the video's real subject. */
    data class Found(val locations: List<ResolvedLocation>) : ShareProcessingProgress()

    data class NotFound(val message: String) : ShareProcessingProgress()

    data class Failed(val error: Throwable) : ShareProcessingProgress()
}
