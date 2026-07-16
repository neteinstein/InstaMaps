package org.neteinstein.instamaps.feature.share.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.neteinstein.instamaps.feature.geocoding.domain.SearchPlaceUseCase
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesFromDescriptionUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.LocationCandidate
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoAnalysisProgress

/**
 * Orchestrates the whole share-to-maps-link pipeline. Tries
 * [extractLocationCandidatesFromDescriptionUseCase] first - a single lightweight metadata fetch
 * against the shared video's own caption/description, which often already names the place - and
 * only falls back to the much more expensive download+OCR pipeline in
 * [extractLocationCandidatesUseCase] if that comes up empty or fails to resolve to a real place.
 * Either source's ranked candidates are resolved the same way (see [resolveBest]).
 *
 * [LocationCandidate.PlaceName] candidates are resolved through [searchPlaceUseCase] against the
 * Places SDK, falling back to the next-ranked candidate if a search comes up empty - the caption
 * text or OCR/entity extraction step can be ambiguous or misread a sign, so the second-best guess
 * is worth trying before giving up. [LocationCandidate.Coordinates] candidates skip geocoding
 * entirely: Google Maps' universal link accepts a bare "lat,lng" query natively, and
 * [SearchPlaceUseCase] only supports text search.
 */
class ProcessSharedUrlUseCase(
    private val extractLocationCandidatesFromDescriptionUseCase: ExtractLocationCandidatesFromDescriptionUseCase,
    private val extractLocationCandidatesUseCase: ExtractLocationCandidatesUseCase,
    private val searchPlaceUseCase: SearchPlaceUseCase,
) {
    operator fun invoke(url: String): Flow<ShareProcessingProgress> =
        flow {
            emit(ShareProcessingProgress.CheckingDescription)
            val descriptionCandidates = extractLocationCandidatesFromDescriptionUseCase(url)
            if (descriptionCandidates.isNotEmpty()) {
                emit(ShareProcessingProgress.Geocoding)
                val found = resolveBest(descriptionCandidates)
                if (found != null) {
                    emit(found)
                    return@flow
                }
            }

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
        val found = resolveBest(candidates)
        emit(found ?: ShareProcessingProgress.NotFound("Couldn't match any detected location to a real place"))
    }

    private suspend fun resolveBest(candidates: List<LocationCandidate>): ShareProcessingProgress.Found? {
        for (candidate in candidates) {
            val found = tryResolve(candidate)
            if (found != null) return found
        }
        return null
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
