package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * On-device address extraction from raw OCR/caption text (ML Kit Entity Extraction). Returns the
 * matched address substrings verbatim - see
 * [org.neteinstein.instamaps.feature.videoprocessing.data.MlKitEntityExtractionRepository] for why
 * there's no structured street/city breakdown available.
 */
interface EntityExtractionRepository {
    suspend fun extractAddresses(text: String): Result<List<String>>
}
