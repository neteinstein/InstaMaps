package org.neteinstein.instamaps.feature.geocoding.domain

import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

/**
 * Boundary between the location-resolution domain layer and whatever AI service interprets raw
 * text into a place (Gemini 1.5 Flash in production; fakeable in tests).
 */
interface LocationRepository {
    /**
     * Given all raw text gathered from a video (caption + OCR'd frames), asks the AI to identify
     * the place being discussed and returns a [MapsDestination] ready to open in Google Maps.
     */
    suspend fun resolveFromText(text: String): Result<MapsDestination>
}
