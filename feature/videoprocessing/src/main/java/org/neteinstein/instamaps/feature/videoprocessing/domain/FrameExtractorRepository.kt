package org.neteinstein.instamaps.feature.videoprocessing.domain

import kotlinx.coroutines.flow.Flow

/**
 * Extracts frames from [video] roughly [intervalMs] apart, one pre-downscaled, keyframe-snapped
 * [VideoFrame] at a time. Implementations must emit frames lazily as they're decoded (a cold,
 * IO-bound producer) rather than decoding the whole video up front, so the first frame can reach
 * an OCR consumer in [ExtractLocationCandidatesUseCase] while later frames are still decoding.
 */
interface FrameExtractorRepository {
    fun extractFrames(
        video: DownloadedVideo,
        intervalMs: Long = 1_500,
    ): Flow<VideoFrame>
}
