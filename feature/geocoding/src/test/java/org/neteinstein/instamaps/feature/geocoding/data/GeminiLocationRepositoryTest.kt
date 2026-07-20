package org.neteinstein.instamaps.feature.geocoding.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DefaultDispatcherProvider
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository

private class FakeAppSettingsRepository(
    private val apiKey: String?,
) : AppSettingsRepository {
    override fun observeGeminiApiKey(): Flow<String?> = flowOf(apiKey)

    override suspend fun saveGeminiApiKey(apiKey: String) = error("not used in this test")
}

class GeminiLocationRepositoryTest {
    @Test
    fun `fails with MissingConfiguration when no api key is saved`() =
        runTest {
            val repository =
                GeminiLocationRepository(
                    settingsRepository = FakeAppSettingsRepository(apiKey = null),
                    dispatcherProvider = DefaultDispatcherProvider(),
                )

            val result = repository.resolveFromText("Some video text about a restaurant")

            assertTrue(result.exceptionOrNull() is AppError.MissingConfiguration)
        }

    @Test
    fun `fails with MissingConfiguration when the saved api key is blank`() =
        runTest {
            val repository =
                GeminiLocationRepository(
                    settingsRepository = FakeAppSettingsRepository(apiKey = "   "),
                    dispatcherProvider = DefaultDispatcherProvider(),
                )

            val result = repository.resolveFromText("Some video text about a restaurant")

            assertTrue(result.exceptionOrNull() is AppError.MissingConfiguration)
        }
}
