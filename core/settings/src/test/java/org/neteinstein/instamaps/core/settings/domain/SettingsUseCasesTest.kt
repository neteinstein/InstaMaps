package org.neteinstein.instamaps.core.settings.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError

private class FakeAppSettingsRepository(
    initial: String? = null,
) : AppSettingsRepository {
    private val key = MutableStateFlow(initial)
    var lastSaved: String? = null

    override fun observeGeminiApiKey(): Flow<String?> = key

    override suspend fun saveGeminiApiKey(apiKey: String) {
        lastSaved = apiKey
        key.value = apiKey.trim().ifEmpty { null }
    }
}

class ObserveGeminiApiKeyUseCaseTest {
    @Test
    fun `emits the value currently held by the repository`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = "AIzaSyExample")
            val useCase = ObserveGeminiApiKeyUseCase(repository)

            useCase().test {
                assertEquals("AIzaSyExample", awaitItem())
            }
        }

    @Test
    fun `emits null when nothing has been saved`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = null)
            val useCase = ObserveGeminiApiKeyUseCase(repository)

            useCase().test {
                assertEquals(null, awaitItem())
            }
        }
}

class SaveGeminiApiKeyUseCaseTest {
    @Test
    fun `delegates to the repository`() =
        runTest {
            val repository = FakeAppSettingsRepository()
            val useCase = SaveGeminiApiKeyUseCase(repository)

            useCase("AIzaSyExample")

            assertEquals("AIzaSyExample", repository.lastSaved)
        }
}

class IsGeminiApiKeyConfiguredUseCaseTest {
    @Test
    fun `is true once a non-blank key is saved`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = null)
            val useCase = IsGeminiApiKeyConfiguredUseCase(repository)

            useCase().test {
                assertEquals(false, awaitItem())
                repository.saveGeminiApiKey("AIzaSyExample")
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `is false when the saved key is blank`() =
        runTest {
            val repository = FakeAppSettingsRepository(initial = "   ")
            val useCase = IsGeminiApiKeyConfiguredUseCase(repository)

            useCase().test {
                assertEquals(false, awaitItem())
            }
        }
}

private class FakeGeminiApiKeyValidator(
    private val result: Result<ApiKeyValidity>,
) : GeminiApiKeyValidator {
    var lastValidatedKey: String? = null

    override suspend fun validate(apiKey: String): Result<ApiKeyValidity> {
        lastValidatedKey = apiKey
        return result
    }
}

class ValidateGeminiApiKeyUseCaseTest {
    @Test
    fun `delegates to the validator and returns its result`() =
        runTest {
            val validator = FakeGeminiApiKeyValidator(result = Result.success(ApiKeyValidity.VALID))
            val useCase = ValidateGeminiApiKeyUseCase(validator)

            val result = useCase("AIzaSyExample")

            assertEquals("AIzaSyExample", validator.lastValidatedKey)
            assertEquals(ApiKeyValidity.VALID, result.getOrNull())
        }

    @Test
    fun `propagates a failure when validity could not be determined`() =
        runTest {
            val failure = Result.failure<ApiKeyValidity>(AppError.Network("no network"))
            val validator = FakeGeminiApiKeyValidator(result = failure)
            val useCase = ValidateGeminiApiKeyUseCase(validator)

            val result = useCase("AIzaSyExample")

            assertEquals(true, result.isFailure)
        }
}
