package org.neteinstein.instamaps.feature.geocoding.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.describeOrDefault
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import org.neteinstein.instamaps.feature.geocoding.domain.LocationRepository
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import java.net.HttpURLConnection
import java.net.URL

/**
 * [LocationRepository] backed by the Gemini 1.5 Flash REST API. Sends all collected text
 * (video caption + OCR frames) as a single prompt and parses the response as a Google Maps
 * query string ([MapsDestination.query]).
 *
 * Uses the standard [java.net.HttpURLConnection] and Android's built-in [org.json] for JSON
 * handling - no additional SDK dependency needed.
 *
 * The Gemini API key is read from [AppSettingsRepository] on every call so a key saved on the
 * Settings screen takes effect immediately without restarting the app.
 */
class GeminiLocationRepository(
    private val settingsRepository: AppSettingsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : LocationRepository {
    override suspend fun resolveFromText(text: String): Result<MapsDestination> {
        val apiKey = settingsRepository.observeGeminiApiKey().first()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                AppError.MissingConfiguration("No Gemini API key configured - add one in Settings"),
            )
        }

        return withContext(dispatcherProvider.io) {
            safeCall(
                mapError = { throwable ->
                    when (throwable) {
                        is AppError -> throwable
                        else -> AppError.Network(throwable.describeOrDefault("Gemini API call failed"), throwable)
                    }
                },
            ) {
                val prompt = buildPrompt(text)
                val requestBody = buildRequestBody(prompt)
                val responseText = postToGemini(apiKey, requestBody)
                parseLocation(responseText)
            }
        }
    }

    private fun buildPrompt(text: String): String =
        "These are caption and text from a video that talks about a specific place. " +
            "From those, determine the place and return the Google Maps location. " +
            "Return only the place name and address (for example: \"Eiffel Tower, Paris, France\"). " +
            "If you cannot identify a specific place from the text, return only the word UNKNOWN. " +
            "Do not include any explanation or other text.\n\n" +
            text.take(MAX_INPUT_CHARS)

    private fun buildRequestBody(prompt: String): String =
        JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            ).toString()

    private fun postToGemini(
        apiKey: String,
        requestBody: String,
    ): String {
        val connection = URL("$GEMINI_API_URL?key=$apiKey").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody =
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText().orEmpty()
                throw AppError.Network("Gemini API error $responseCode: $errorBody")
            }

            return connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseLocation(responseText: String): MapsDestination {
        val location =
            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

        if (location.isBlank() || location.equals("UNKNOWN", ignoreCase = true)) {
            throw AppError.NotFound("Gemini could not identify a specific place from the provided text")
        }

        return MapsDestination(query = location)
    }

    private companion object {
        const val GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"

        /** Caps the text sent to Gemini to avoid excessively large requests. */
        const val MAX_INPUT_CHARS = 10_000
    }
}
