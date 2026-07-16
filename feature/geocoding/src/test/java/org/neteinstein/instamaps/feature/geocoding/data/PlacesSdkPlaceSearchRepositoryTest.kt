package org.neteinstein.instamaps.feature.geocoding.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository

private class FakeAppSettingsRepository(
    private val apiKey: String?,
) : AppSettingsRepository {
    override fun observePlacesApiKey(): Flow<String?> = flowOf(apiKey)

    override suspend fun savePlacesApiKey(apiKey: String) = error("not used in this test")
}

class PlacesSdkPlaceSearchRepositoryTest {
    @Test
    fun `fails with MissingConfiguration when no api key is saved`() =
        runTest {
            val repository =
                PlacesSdkPlaceSearchRepository(
                    context = mock<Context>(),
                    settingsRepository = FakeAppSettingsRepository(apiKey = null),
                )

            val result = repository.searchByText("Dishoom London")

            assertTrue(result.exceptionOrNull() is AppError.MissingConfiguration)
        }

    @Test
    fun `fails with MissingConfiguration when the saved api key is blank`() =
        runTest {
            val repository =
                PlacesSdkPlaceSearchRepository(
                    context = mock<Context>(),
                    settingsRepository = FakeAppSettingsRepository(apiKey = "   "),
                )

            val result = repository.searchByText("Dishoom London")

            assertTrue(result.exceptionOrNull() is AppError.MissingConfiguration)
        }
}
