package org.neteinstein.instamaps.feature.geocoding.domain

/**
 * Boundary between the geocoding domain layer and whatever SDK resolves free text into places
 * (Places SDK "New" in production; fakeable in tests).
 */
interface PlaceSearchRepository {
    suspend fun searchByText(query: String): Result<List<GeocodedPlace>>
}
