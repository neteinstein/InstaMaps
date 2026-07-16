package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.neteinstein.instamaps.core.common.LatLng

/**
 * Pure regex-based text parser run against any block of location-adjacent text this app gathers -
 * a video's caption/description (see [ExtractLocationCandidatesFromDescriptionUseCase]) or OCR'd
 * on-screen text from [ExtractLocationCandidatesUseCase]'s video frames - via the shared
 * [LocationTextAnalyzer] (see `feature:share`'s `ProcessSharedUrlUseCase` for how the ranked
 * candidates either source produces get resolved to a real place). Ported/generalized from the
 * original single-module app's caption parser, extended to return every signal found (ranked by
 * [LocationCandidate.confidence]) instead of only the first match, since callers gather text from
 * multiple sources/frames and want to try the best-ranked candidate first rather than committing
 * to whichever pattern happened to run first.
 *
 * Tries, in order of how unambiguous the signal is:
 * 1. An explicit marker (`Location:`, `loc:`, `place:`, `at:`, `in:`, 📍, 🗺️).
 * 2. A literal "lat, lng" coordinate pair.
 * 3. A CamelCase hashtag that looks like a place name (e.g. `#NewYorkCity`).
 */
class LocationTextParser {
    fun parse(text: String): List<LocationCandidate> {
        if (text.isBlank()) return emptyList()

        return listOfNotNull(
            extractExplicitLocation(text),
            extractCoordinates(text),
            extractHashtagLocation(text),
        )
    }

    private fun extractExplicitLocation(text: String): LocationCandidate.PlaceName? {
        for (pattern in EXPLICIT_PATTERNS) {
            val name = pattern.find(text)?.groupValues?.get(1)?.trim()
            if (!name.isNullOrBlank()) {
                return LocationCandidate.PlaceName(text = name, confidence = EXPLICIT_CONFIDENCE)
            }
        }
        return null
    }

    private fun extractCoordinates(text: String): LocationCandidate.Coordinates? {
        val match = COORDINATE_PATTERN.find(text) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        // Out-of-range values (e.g. a price/ratio that happens to look like "x.xx, y.yy") are a
        // false positive, not a malformed coordinate - LatLng's own validation is the source of
        // truth for what counts as a real coordinate pair, so we defer to it rather than
        // duplicating range checks here.
        val latLng = runCatching { LatLng(latitude = latitude, longitude = longitude) }.getOrNull() ?: return null
        return LocationCandidate.Coordinates(latLng = latLng, confidence = COORDINATES_CONFIDENCE)
    }

    private fun extractHashtagLocation(text: String): LocationCandidate.PlaceName? {
        val match = HASHTAG_PATTERN.find(text) ?: return null
        val name =
            match.groupValues[1]
                .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                .trim()
        return if (name.isNotBlank()) {
            LocationCandidate.PlaceName(text = name, confidence = HASHTAG_CONFIDENCE)
        } else {
            null
        }
    }

    private companion object {
        val EXPLICIT_PATTERNS =
            listOf(
                Regex("\\b(?:location|loc|place|at|in):\\s*([^\\n#@]+)", RegexOption.IGNORE_CASE),
                Regex("📍\\s*([^\\n#@]+)"),
                Regex("🗺️?\\s*([^\\n#@]+)"),
            )
        val COORDINATE_PATTERN = Regex("(-?\\d{1,3}\\.\\d+),\\s*(-?\\d{1,3}\\.\\d+)")
        val HASHTAG_PATTERN = Regex("#([A-Z][a-zA-Z]+(?:[A-Z][a-zA-Z]+)+)")

        const val EXPLICIT_CONFIDENCE = 0.9f
        const val COORDINATES_CONFIDENCE = 0.95f
        const val HASHTAG_CONFIDENCE = 0.5f
    }
}
