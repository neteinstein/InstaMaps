package org.neteinstein.instamaps.feature.geocoding.domain

import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

/**
 * Sends all text collected from a shared video (caption + OCR frames) to the Gemini API via
 * [LocationRepository] and returns the resolved [MapsDestination]. Returns a [Result.failure]
 * wrapping [AppError.NotFound] when Gemini cannot identify a specific place, or another
 * [AppError] subtype for network/config failures - see [LocationRepository] for details.
 */
class ResolveLocationUseCase(
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke(text: String): Result<MapsDestination> {
        if (text.isBlank()) {
            return Result.failure(AppError.InvalidInput("No text provided to resolve location from"))
        }
        return locationRepository.resolveFromText(text.trim())
    }
}
