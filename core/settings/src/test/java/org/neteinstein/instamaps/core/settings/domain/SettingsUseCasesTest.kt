package org.neteinstein.instamaps.core.settings.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeAppSettingsRepository(
    initial: String? = null,
) : AppSettingsRepository {
    private val key = MutableStateFlow(initial)
    var lastSaved: String? = null

    override fun observePlacesApiKey(): Flow<String?> = key

    override suspend fun savePlacesApiKey(apiKey: String) {
        lastSaved = apiKey
        key.value = apiKey.trim().ifEmpty { null }
    }
}

class ObservePlacesApiKeyUseCaseTest {
    @Test
    fun `emits the value currently held by the repository`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = "AIzaSyExample")
            val useCase = ObservePlacesApiKeyUseCase(repository)

            useCase().test {
                assertEquals("AIzaSyExample", awaitItem())
            }
        }

    @Test
    fun `emits null when nothing has been saved`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = null)
            val useCase = ObservePlacesApiKeyUseCase(repository)

            useCase().test {
                assertEquals(null, awaitItem())
            }
        }
}

class SavePlacesApiKeyUseCaseTest {
    @Test
    fun `delegates to the repository`() =
        runTest {
            val repository = FakeAppSettingsRepository()
            val useCase = SavePlacesApiKeyUseCase(repository)

            useCase("AIzaSyExample")

            assertEquals("AIzaSyExample", repository.lastSaved)
        }
}

class IsPlacesApiKeyConfiguredUseCaseTest {
    @Test
    fun `is true once a non-blank key is saved`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = null)
            val useCase = IsPlacesApiKeyConfiguredUseCase(repository)

            useCase().test {
                assertEquals(false, awaitItem())
                repository.savePlacesApiKey("AIzaSyExample")
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `is false when the saved key is blank`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = "   ")
            val useCase = IsPlacesApiKeyConfiguredUseCase(repository)

            useCase().test {
                assertEquals(false, awaitItem())
            }
        }
}
