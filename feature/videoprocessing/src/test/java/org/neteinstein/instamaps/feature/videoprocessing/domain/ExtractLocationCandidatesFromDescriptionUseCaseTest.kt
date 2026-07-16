package org.neteinstein.instamaps.feature.videoprocessing.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError

private class FakeVideoMetadataRepository(
    private val result: Result<String>,
) : VideoMetadataRepository {
    var requestedUrl: String? = null

    override suspend fun fetchDescription(url: String): Result<String> {
        requestedUrl = url
        return result
    }
}

class ExtractLocationCandidatesFromDescriptionUseCaseTest {
    private val analyzer = LocationTextAnalyzer(FakeEntityExtractionRepository(), LocationTextParser())

    @Test
    fun `returns ranked candidates parsed from the fetched description`() =
        runTest {
            val useCase =
                ExtractLocationCandidatesFromDescriptionUseCase(
                    videoMetadataRepository = FakeVideoMetadataRepository(Result.success("Brunch at 📍 Dishoom Shoreditch")),
                    locationTextAnalyzer = analyzer,
                )

            val candidates = useCase("https://instagram.com/reel/abc")

            val topCandidate = candidates.first() as LocationCandidate.PlaceName
            assertEquals("Dishoom Shoreditch", topCandidate.text)
        }

    @Test
    fun `returns an empty list when the description is blank`() =
        runTest {
            val useCase =
                ExtractLocationCandidatesFromDescriptionUseCase(
                    videoMetadataRepository = FakeVideoMetadataRepository(Result.success("")),
                    locationTextAnalyzer = analyzer,
                )

            val candidates = useCase("https://instagram.com/reel/abc")

            assertTrue(candidates.isEmpty())
        }

    @Test
    fun `returns an empty list when the metadata fetch fails`() =
        runTest {
            val useCase =
                ExtractLocationCandidatesFromDescriptionUseCase(
                    videoMetadataRepository = FakeVideoMetadataRepository(Result.failure(AppError.Network("boom"))),
                    locationTextAnalyzer = analyzer,
                )

            val candidates = useCase("https://instagram.com/reel/abc")

            assertTrue(candidates.isEmpty())
        }

    @Test
    fun `fetches metadata for the exact url it was given`() =
        runTest {
            val metadataRepository = FakeVideoMetadataRepository(Result.success(""))
            val useCase =
                ExtractLocationCandidatesFromDescriptionUseCase(
                    videoMetadataRepository = metadataRepository,
                    locationTextAnalyzer = analyzer,
                )

            useCase("https://instagram.com/reel/abc")

            assertEquals("https://instagram.com/reel/abc", metadataRepository.requestedUrl)
        }
}
