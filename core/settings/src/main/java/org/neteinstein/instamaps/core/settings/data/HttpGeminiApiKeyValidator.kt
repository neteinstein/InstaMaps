package org.neteinstein.instamaps.core.settings.data

import kotlinx.coroutines.withContext
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.describeOrDefault
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.core.settings.domain.ApiKeyValidity
import org.neteinstein.instamaps.core.settings.domain.GeminiApiKeyValidator
import java.net.HttpURLConnection
import java.net.URL

/**
 * [GeminiApiKeyValidator] backed by Gemini's `models.list` endpoint. Listing models only requires
 * an authenticated request - unlike `generateContent` (used for real location resolution, see
 * `feature:geocoding`'s `GeminiLocationRepository`) - so this confirms a key works without
 * spending any generation quota, keeping the check "quick" as intended. A 200 response means the
 * key was accepted; Gemini rejects a bad key with 400/401/403 (see [classifyResponseCode]).
 * Short, explicit timeouts keep a stalled connection from leaving the Settings screen's Save
 * button stuck in its loading state indefinitely.
 */
class HttpGeminiApiKeyValidator(
    private val dispatcherProvider: DispatcherProvider,
) : GeminiApiKeyValidator {
    override suspend fun validate(apiKey: String): Result<ApiKeyValidity> {
        // A blank key is never valid and isn't worth a round trip to confirm.
        if (apiKey.isBlank()) return Result.success(ApiKeyValidity.INVALID)

        return withContext(dispatcherProvider.io) {
            safeCall(
                mapError = { throwable ->
                    when (throwable) {
                        is AppError -> throwable
                        else -> AppError.Network(throwable.describeOrDefault("Could not verify the Gemini API key"), throwable)
                    }
                },
            ) {
                val connection = URL("$GEMINI_MODELS_URL?key=$apiKey").openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = TIMEOUT_MILLIS
                    connection.readTimeout = TIMEOUT_MILLIS
                    val responseCode = connection.responseCode
                    classifyResponseCode(responseCode) ?: run {
                        val errorBody =
                            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                        throw AppError.Network("Gemini API error $responseCode: $errorBody")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    internal companion object {
        const val GEMINI_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TIMEOUT_MILLIS = 10_000

        /**
         * Maps an HTTP response code from [GEMINI_MODELS_URL] to the [ApiKeyValidity] it implies,
         * or `null` if the code doesn't clearly mean either - e.g. a 5xx or a rate limit, which
         * say nothing about the key itself. Pulled out as a pure function so the decision logic
         * is unit-testable without a real network call.
         */
        fun classifyResponseCode(responseCode: Int): ApiKeyValidity? =
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> ApiKeyValidity.VALID
                HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    ApiKeyValidity.INVALID
                else -> null
            }
    }
}
