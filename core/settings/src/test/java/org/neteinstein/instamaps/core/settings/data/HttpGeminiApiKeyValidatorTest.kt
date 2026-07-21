package org.neteinstein.instamaps.core.settings.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.neteinstein.instamaps.core.common.DefaultDispatcherProvider
import org.neteinstein.instamaps.core.settings.domain.ApiKeyValidity
import java.net.HttpURLConnection

class HttpGeminiApiKeyValidatorTest {
    @Test
    fun `a blank key is reported invalid without a network call`() =
        runTest {
            val validator = HttpGeminiApiKeyValidator(dispatcherProvider = DefaultDispatcherProvider())

            val result = validator.validate("   ")

            assertEquals(ApiKeyValidity.INVALID, result.getOrNull())
        }

    @Test
    fun `classifyResponseCode maps 200 to VALID`() {
        assertEquals(
            ApiKeyValidity.VALID,
            HttpGeminiApiKeyValidator.classifyResponseCode(HttpURLConnection.HTTP_OK),
        )
    }

    @Test
    fun `classifyResponseCode maps 400, 401 and 403 to INVALID`() {
        assertEquals(
            ApiKeyValidity.INVALID,
            HttpGeminiApiKeyValidator.classifyResponseCode(HttpURLConnection.HTTP_BAD_REQUEST),
        )
        assertEquals(
            ApiKeyValidity.INVALID,
            HttpGeminiApiKeyValidator.classifyResponseCode(HttpURLConnection.HTTP_UNAUTHORIZED),
        )
        assertEquals(
            ApiKeyValidity.INVALID,
            HttpGeminiApiKeyValidator.classifyResponseCode(HttpURLConnection.HTTP_FORBIDDEN),
        )
    }

    @Test
    fun `classifyResponseCode is null for a code that says nothing about the key itself`() {
        assertEquals(
            null,
            HttpGeminiApiKeyValidator.classifyResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR),
        )
        assertEquals(
            null,
            HttpGeminiApiKeyValidator.classifyResponseCode(TOO_MANY_REQUESTS),
        )
    }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}
