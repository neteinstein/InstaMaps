package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Cheap first pass tried before the full download+OCR pipeline in [ExtractLocationCandidatesUseCase]:
 * many Instagram/TikTok captions already name the place on-screen text would otherwise have to
 * reveal frame-by-frame (e.g. "Brunch at Dishoom Shoreditch 😍 📍"). Fetches only the shared
 * video's metadata - a single `yt-dlp --dump-json` call, no media bytes - via
 * [videoMetadataRepository], then runs its description through the same [locationTextAnalyzer]
 * used for per-frame OCR text, so both sources are read identically. See `feature:share`'s
 * `ProcessSharedUrlUseCase` for how this result and the video-OCR fallback are sequenced.
 *
 * Returns a plain (already-unwrapped) ranked list rather than a `Result`: a failed/empty metadata
 * fetch just means this fast path found nothing, not that the whole share failed - the caller
 * falls back to the video pipeline regardless of why this came up empty.
 */
class ExtractLocationCandidatesFromDescriptionUseCase(
    private val videoMetadataRepository: VideoMetadataRepository,
    private val locationTextAnalyzer: LocationTextAnalyzer,
) {
    suspend operator fun invoke(url: String): List<LocationCandidate> {
        val description = videoMetadataRepository.fetchDescription(url).getOrNull().orEmpty()
        return locationTextAnalyzer.analyze(description).sortedByDescending { it.confidence }
    }
}
