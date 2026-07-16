package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.neteinstein.instamaps.core.common.LatLng

/**
 * A possible location mentioned somewhere in a video's caption or on-screen text, before it has
 * been resolved to a real place via geocoding. [confidence] (roughly 0-1) lets
 * [ExtractLocationCandidatesUseCase] rank candidates gathered from multiple frames/sources so the
 * most-likely one is tried first against the Places SDK.
 */
sealed class LocationCandidate {
    abstract val confidence: Float

    data class PlaceName(
        val text: String,
        override val confidence: Float,
    ) : LocationCandidate()

    data class Coordinates(
        val latLng: LatLng,
        override val confidence: Float,
    ) : LocationCandidate()
}
