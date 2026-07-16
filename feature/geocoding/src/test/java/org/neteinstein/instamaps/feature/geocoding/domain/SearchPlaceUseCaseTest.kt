package org.neteinstein.instamaps.feature.geocoding.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.LatLng

private class FakePlaceSearchRepository(
    private val result: Result<List<GeocodedPlace>> = Result.success(emptyList()),
) : PlaceSearchRepository {
    var lastQuery: String? = null

    override suspend fun searchByText(query: String): Result<List<GeocodedPlace>> {
        lastQuery = query
        return result
    }
}

class SearchPlaceUseCaseTest {
    private val samplePlace =
        GeocodedPlace(
            placeId = "place-1",
            name = "Dishoom London",
            address = "12 Upper St Martin's Lane, London",
            latLng = LatLng(51.5117, -0.1275),
        )

    @Test
    fun `returns the first ranked result on success`() =
        runTest {
            val repository = FakePlaceSearchRepository(Result.success(listOf(samplePlace)))
            val useCase = SearchPlaceUseCase(repository)

            val result = useCase("Dishoom London")

            assertEquals(samplePlace, result.getOrNull())
        }

    @Test
    fun `trims the query before delegating to the repository`() =
        runTest {
            val repository = FakePlaceSearchRepository(Result.success(listOf(samplePlace)))
            val useCase = SearchPlaceUseCase(repository)

            useCase("  Dishoom London  ")

            assertEquals("Dishoom London", repository.lastQuery)
        }

    @Test
    fun `fails with NotFound when the repository returns no places`() =
        runTest {
            val repository = FakePlaceSearchRepository(Result.success(emptyList()))
            val useCase = SearchPlaceUseCase(repository)

            val result = useCase("somewhere that does not exist")

            assertTrue(result.exceptionOrNull() is AppError.NotFound)
        }

    @Test
    fun `fails with InvalidInput without calling the repository for a blank query`() =
        runTest {
            val repository = FakePlaceSearchRepository()
            val useCase = SearchPlaceUseCase(repository)

            val result = useCase("   ")

            assertTrue(result.exceptionOrNull() is AppError.InvalidInput)
            assertEquals(null, repository.lastQuery)
        }

    @Test
    fun `propagates repository failure`() =
        runTest {
            val failure = AppError.Network("boom")
            val repository = FakePlaceSearchRepository(Result.failure(failure))
            val useCase = SearchPlaceUseCase(repository)

            val result = useCase("Dishoom London")

            assertEquals(failure, result.exceptionOrNull())
        }
}
