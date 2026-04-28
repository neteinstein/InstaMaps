package org.neteinstein.instamaps.domain.usecase

import org.neteinstein.instamaps.domain.model.LocationInfo

class ExtractLocationUseCase {
    operator fun invoke(text: String): LocationInfo? {
        if (text.isBlank()) return null

        return extractExplicitLocation(text)
            ?: extractHashtagLocation(text)
            ?: extractCoordinates(text)
    }

    private fun extractExplicitLocation(text: String): LocationInfo? {
        val locationPatterns =
            listOf(
                Regex("\\b(?:location|loc|place|at|in):\\s*([^\\n#@]+)", RegexOption.IGNORE_CASE),
                Regex("📍\\s*([^\\n#@]+)"),
                Regex("🗺️?\\s*([^\\n#@]+)"),
            )
        for (pattern in locationPatterns) {
            val match = pattern.find(text) ?: continue
            val locationName = match.groupValues[1].trim()
            if (locationName.isNotBlank()) {
                return LocationInfo(name = locationName, rawText = text)
            }
        }
        return null
    }

    private fun extractHashtagLocation(text: String): LocationInfo? {
        val hashtagPattern = Regex("#([A-Z][a-zA-Z]+(?:[A-Z][a-zA-Z]+)+)")
        val match = hashtagPattern.find(text) ?: return null
        val locationName =
            match.groupValues[1]
                .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
                .trim()
        return if (locationName.isNotBlank()) {
            LocationInfo(name = locationName, rawText = text)
        } else {
            null
        }
    }

    private fun extractCoordinates(text: String): LocationInfo? {
        val coordPattern =
            Regex(
                "(-?\\d{1,3}\\.\\d+),\\s*(-?\\d{1,3}\\.\\d+)",
            )
        val match = coordPattern.find(text) ?: return null
        val lat = match.groupValues[1]
        val lng = match.groupValues[2]
        return LocationInfo(name = "$lat, $lng", rawText = text)
    }
}
