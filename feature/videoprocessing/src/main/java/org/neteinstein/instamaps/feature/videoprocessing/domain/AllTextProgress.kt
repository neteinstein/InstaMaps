package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Progress events emitted by [CollectAllTextUseCase] while gathering caption text and
 * video-frame OCR text from a shared URL. Consumed by `feature:share`'s
 * `ProcessSharedUrlUseCase`, which maps each event to the corresponding
 * `ShareProcessingProgress` step shown in the UI.
 */
sealed class AllTextProgress {
    /** Fetching the video's caption/description metadata (fast, no media download). */
    data object CheckingDescription : AllTextProgress()

    /** Downloading the video file. */
    data object Downloading : AllTextProgress()

    /** Video downloaded; extracting frames for OCR. */
    data object ExtractingFrames : AllTextProgress()

    /** OCR completed for the [frameIndex]-th frame (1-based). */
    data class AnalyzingFrame(val frameIndex: Int) : AllTextProgress()

    /** All text gathered successfully. [texts] is the deduplicated union of the caption and every
     *  non-blank OCR result across all extracted frames. */
    data class Completed(val texts: List<String>) : AllTextProgress()

    /** A fatal error occurred (download failure, frame-extraction failure, etc.). */
    data class Failed(val error: Throwable) : AllTextProgress()
}
