package org.neteinstein.instamaps.feature.geocoding.domain

/**
 * Boundary between the location-resolution domain layer and whatever AI service interprets raw
 * text into a place (Gemini Flash in production; fakeable in tests).
 */
interface LocationRepository {
    /**
     * Given all raw text gathered from a video (caption + OCR'd frames), asks the AI to identify
     * every real-world place being discussed and returns them as a non-empty list of
     * [ResolvedLocation], ordered from most to least likely to be the one the video is actually
     * about.
     */
    suspend fun resolveFromText(text: String): Result<List<ResolvedLocation>>
}
