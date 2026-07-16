package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Runs a single block of text through both signal sources this app knows how to read a location
 * from - [LocationTextParser]'s regex patterns and [EntityExtractionRepository]'s ML Kit address
 * detection - and merges the results into one candidate list. Shared by
 * [ExtractLocationCandidatesUseCase] (per video frame's OCR'd text) and
 * [ExtractLocationCandidatesFromDescriptionUseCase] (a shared video's caption/description text)
 * so both text sources are analyzed identically instead of duplicating the merge logic per caller.
 */
class LocationTextAnalyzer(
    private val entityExtractionRepository: EntityExtractionRepository,
    private val locationTextParser: LocationTextParser,
) {
    suspend fun analyze(text: String): List<LocationCandidate> {
        if (text.isBlank()) return emptyList()

        val addresses = entityExtractionRepository.extractAddresses(text).getOrNull().orEmpty()
        val addressCandidates = addresses.map { LocationCandidate.PlaceName(text = it, confidence = ADDRESS_CONFIDENCE) }
        return locationTextParser.parse(text) + addressCandidates
    }

    private companion object {
        const val ADDRESS_CONFIDENCE = 0.85f
    }
}
