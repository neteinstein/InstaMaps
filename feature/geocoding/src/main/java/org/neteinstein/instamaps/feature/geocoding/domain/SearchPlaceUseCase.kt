package org.neteinstein.instamaps.feature.geocoding.domain

import org.neteinstein.instamaps.core.common.AppError

/**
 * Resolves free-form location text (an OCR'd sign, a caption's "Location: ..." line, a raw
 * "lat, lng" string) down to the single best-matching real-world place. [PlaceSearchRepository]
 * can return several candidates; this use case picks the top-ranked one so callers get a simple
 * "did we find it" result instead of having to rank places themselves.
 */
class SearchPlaceUseCase(
    private val placeSearchRepository: PlaceSearchRepository,
) {
    suspend operator fun invoke(query: String): Result<GeocodedPlace> {
        if (query.isBlank()) {
            return Result.failure(AppError.InvalidInput("Search query must not be blank"))
        }

        return placeSearchRepository.searchByText(query.trim()).fold(
            onSuccess = { places ->
                places.firstOrNull()
                    ?.let { Result.success(it) }
                    ?: Result.failure(AppError.NotFound("No place found for \"$query\""))
            },
            onFailure = { Result.failure(it) },
        )
    }
}
