package org.neteinstein.instamaps.feature.geocoding.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

private class FakeLocationRepository(
    private val result: Result<MapsDestination>,
) : LocationRepository {
    var lastTextReceived: String? = null

    override suspend fun resolveFromText(text: String): Result<MapsDestination> {
        lastTextReceived = text
        return result
    }
}

class ResolveLocationUseCaseTest {
    private val sampleDestination = MapsDestination(query = "Eiffel Tower, Paris, France")

    @Test
    fun `returns success from repository when text is non-blank`() =
        runTest {
            val repository = FakeLocationRepository(Result.success(sampleDestination))
            val useCase = ResolveLocationUseCase(repository)

            val result = useCase("some video text about Eiffel Tower")

            assertEquals(sampleDestination, result.getOrNull())
        }

    @Test
    fun `trims whitespace before delegating to repository`() =
        runTest {
            val repository = FakeLocationRepository(Result.success(sampleDestination))
            val useCase = ResolveLocationUseCase(repository)

            useCase("   some text   ")

            assertEquals("some text", repository.lastTextReceived)
        }

    @Test
    fun `returns InvalidInput error when text is blank`() =
        runTest {
            val repository = FakeLocationRepository(Result.success(sampleDestination))
            val useCase = ResolveLocationUseCase(repository)

            val result = useCase("   ")

            assertTrue(result.exceptionOrNull() is AppError.InvalidInput)
        }

    @Test
    fun `propagates repository failure unchanged`() =
        runTest {
            val error = AppError.NotFound("Gemini could not identify a specific place")
            val repository = FakeLocationRepository(Result.failure(error))
            val useCase = ResolveLocationUseCase(repository)

            val result = useCase("some text")

            assertEquals(error, result.exceptionOrNull())
        }

    @Test
    fun `propagates MissingConfiguration error from repository`() =
        runTest {
            val error = AppError.MissingConfiguration("No Gemini API key configured")
            val repository = FakeLocationRepository(Result.failure(error))
            val useCase = ResolveLocationUseCase(repository)

            val result = useCase("some text")

            assertTrue(result.exceptionOrNull() is AppError.MissingConfiguration)
        }
}
