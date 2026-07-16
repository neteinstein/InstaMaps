package org.neteinstein.instamaps.feature.share.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.neteinstein.instamaps.feature.geocoding.domain.SearchPlaceUseCase
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.LocationCandidate
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoAnalysisProgress

/**
 * Orchestrates the whole share-to-maps-link pipeline: runs [extractLocationCandidatesUseCase]
 * against the shared video, then resolves the highest-confidence candidate to a real place.
 *
 * [LocationCandidate.PlaceName] candidates are resolved through [searchPlaceUseCase] against the
 * Places SDK, falling back to the next-ranked candidate if a search comes up empty - the OCR/entity
 * extraction step can misread a sign, so the second-best guess is worth trying before giving up.
 * [LocationCandidate.Coordinates] candidates skip geocoding entirely: Google Maps' universal link
 * accepts a bare "lat,lng" query natively, and [SearchPlaceUseCase] only supports text search.
 */
class ProcessSharedUrlUseCase(
    private val extractLocationCandidatesUseCase: ExtractLocationCandidatesUseCase,
    private val searchPlaceUseCase: SearchPlaceUseCase,
) {
    operator fun invoke(url: String): Flow<ShareProcessingProgress> =
        flow {
            extractLocationCandidatesUseCase(url).collect { progress ->
                when (progress) {
                    is VideoAnalysisProgress.Downloading -> emit(ShareProcessingProgress.Downloading)
                    is VideoAnalysisProgress.ExtractingFrames -> emit(ShareProcessingProgress.ExtractingFrames)
                    is VideoAnalysisProgress.AnalyzingFrame ->
                        emit(ShareProcessingProgress.AnalyzingFrame(progress.frameIndex))
                    is VideoAnalysisProgress.Failed -> emit(ShareProcessingProgress.Failed(progress.error))
                    is VideoAnalysisProgress.Completed -> emitResolution(progress.candidates)
                }
            }
        }

    private suspend fun FlowCollector<ShareProcessingProgress>.emitResolution(candidates: List<LocationCandidate>) {
        if (candidates.isEmpty()) {
            emit(ShareProcessingProgress.NotFound("No location was found in this video"))
            return
        }

        emit(ShareProcessingProgress.Geocoding)
        for (candidate in candidates) {
            val found = tryResolve(candidate)
            if (found != null) {
                emit(found)
                return
            }
        }
        emit(ShareProcessingProgress.NotFound("Couldn't match any detected location to a real place"))
    }

    private suspend fun tryResolve(candidate: LocationCandidate): ShareProcessingProgress.Found? =
        when (candidate) {
            is LocationCandidate.Coordinates -> {
                val latLng = candidate.latLng
                ShareProcessingProgress.Found(
                    destination = MapsDestination(query = "${latLng.latitude},${latLng.longitude}"),
                    displayName = "%.5f, %.5f".format(latLng.latitude, latLng.longitude),
                )
            }
            is LocationCandidate.PlaceName ->
                searchPlaceUseCase(candidate.text).getOrNull()?.let { place ->
                    ShareProcessingProgress.Found(
                        destination = MapsDestination(query = place.name, placeId = place.placeId),
                        displayName = place.name,
                    )
                }
        }
}
