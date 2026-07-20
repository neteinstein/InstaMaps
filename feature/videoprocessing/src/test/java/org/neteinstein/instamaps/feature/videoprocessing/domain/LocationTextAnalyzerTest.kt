package org.neteinstein.instamaps.feature.videoprocessing.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Shared (not file-private) since ExtractLocationCandidatesUseCaseTest and
// ExtractLocationCandidatesFromDescriptionUseCaseTest also need it in this package.
class FakeEntityExtractionRepository(
    private val addresses: List<String> = emptyList(),
) : EntityExtractionRepository {
    override suspend fun extractAddresses(text: String): Result<List<String>> = Result.success(addresses)
}

class LocationTextAnalyzerTest {
    @Test
    fun `returns empty list for blank text without calling entity extraction`() =
        runTest {
            var extractionCalled = false
            val analyzer =
                LocationTextAnalyzer(
                    entityExtractionRepository =
                        object : EntityExtractionRepository {
                            override suspend fun extractAddresses(text: String): Result<List<String>> {
                                extractionCalled = true
                                return Result.success(emptyList())
                            }
                        },
                    locationTextParser = LocationTextParser(),
                )

            val candidates = analyzer.analyze("   ")

            assertTrue(candidates.isEmpty())
            assertTrue(!extractionCalled)
        }

    @Test
    fun `merges regex-parsed candidates with entity-extracted addresses`() =
        runTest {
            val analyzer =
                LocationTextAnalyzer(
                    entityExtractionRepository = FakeEntityExtractionRepository(listOf("221B Baker Street, London")),
                    locationTextParser = LocationTextParser(),
                )

            val candidates = analyzer.analyze("📍 Eiffel Tower, Paris")

            val placeNames = candidates.filterIsInstance<LocationCandidate.PlaceName>().map { it.text }
            assertEquals(listOf("Eiffel Tower, Paris", "221B Baker Street, London"), placeNames)
        }

    @Test
    fun `returns only regex candidates when entity extraction finds nothing`() =
        runTest {
            val analyzer =
                LocationTextAnalyzer(
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                )

            val candidates = analyzer.analyze("Meet me at 48.8566, 2.3522")

            assertEquals(1, candidates.size)
            assertTrue(candidates.single() is LocationCandidate.Coordinates)
        }

    @Test
    fun `collapses an entity-extracted address that duplicates an explicit regex match`() =
        runTest {
            val analyzer =
                LocationTextAnalyzer(
                    entityExtractionRepository = FakeEntityExtractionRepository(listOf("221b baker street, london")),
                    locationTextParser = LocationTextParser(),
                )

            val candidates = analyzer.analyze("📍 221B Baker Street, London")

            val placeNames = candidates.filterIsInstance<LocationCandidate.PlaceName>().map { it.text }
            assertEquals(listOf("221B Baker Street, London"), placeNames)
        }
}
