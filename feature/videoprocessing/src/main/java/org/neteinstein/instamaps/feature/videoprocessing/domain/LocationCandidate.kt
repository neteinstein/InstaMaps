package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.neteinstein.instamaps.core.common.LatLng

/**
 * A possible location mentioned somewhere in a video's caption or on-screen text, before it has
 * been resolved to a real place via geocoding. [confidence] (roughly 0-1) lets
 * [ExtractLocationCandidatesUseCase] rank candidates gathered from multiple frames/sources so the
 * most-likely one is resolved first.
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

/**
 * Collapses candidates that almost certainly name the same real-world place down to a single,
 * best-ranked one. Two situations produce this redundancy in practice:
 * - The exact same signal reappearing (e.g. an on-screen overlay OCR'd identically across many
 *   consecutive video frames).
 * - A lower-confidence heuristic (e.g. [LocationTextParser]'s capitalized-phrase fallback)
 *   rediscovering a substring of what a higher-confidence one already captured (e.g. "Eiffel
 *   Tower" inside an explicit-marker match of "Eiffel Tower, Paris").
 *
 * [Coordinates] are deduplicated on the resolved lat/lng instead of text. The receiver **must**
 * already be sorted by descending confidence - that's what makes "first candidate seen for a
 * given signal" equivalent to "most trustworthy candidate for that signal".
 */
fun List<LocationCandidate>.mostConfidentDistinct(): List<LocationCandidate> {
    val keptPlaceNames = mutableListOf<String>()
    val keptCoordinates = mutableListOf<LatLng>()
    return filter { candidate ->
        when (candidate) {
            is LocationCandidate.PlaceName -> {
                val normalized = candidate.text.trim().lowercase()
                val isRedundant = keptPlaceNames.any { kept -> kept.contains(normalized) || normalized.contains(kept) }
                (!isRedundant).also { keep -> if (keep) keptPlaceNames += normalized }
            }
            is LocationCandidate.Coordinates -> {
                val isRedundant = candidate.latLng in keptCoordinates
                (!isRedundant).also { keep -> if (keep) keptCoordinates += candidate.latLng }
            }
        }
    }
}
