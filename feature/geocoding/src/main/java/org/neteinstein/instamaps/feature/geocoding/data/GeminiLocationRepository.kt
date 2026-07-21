package org.neteinstein.instamaps.feature.geocoding.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.describeOrDefault
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import org.neteinstein.instamaps.feature.geocoding.domain.LocationRepository
import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import java.net.HttpURLConnection
import java.net.URL

/**
 * [LocationRepository] backed by the Gemini Flash REST API. Sends all collected text
 * (video caption + OCR frames) as a single prompt and parses the response as a ranked JSON array
 * of candidate places (see [buildPrompt]/[parseLocations]).
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
    override suspend fun resolveFromText(text: String): Result<List<ResolvedLocation>> {
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
                        // A malformed/non-JSON Gemini response (e.g. it ignores the format
                        // instruction) surfaces as a JSONException from parseLocations - that's a
                        // parsing failure, not a network one, even though it's caught in the same
                        // safeCall block as the HTTP request itself.
                        is JSONException ->
                            AppError.Parsing(throwable.describeOrDefault("Could not parse Gemini's response"), throwable)
                        else -> AppError.Network(throwable.describeOrDefault("Gemini API call failed"), throwable)
                    }
                },
            ) {
                val prompt = buildPrompt(text)
                val requestBody = buildRequestBody(prompt)
                val responseText = postToGemini(apiKey, requestBody)
                parseLocations(responseText)
            }
        }
    }

    private fun buildPrompt(text: String): String =
        "These are the caption and on-screen text from a video that talks about one or more " +
            "specific real-world places. From this text, identify every distinct place a viewer " +
            "could visit, ordered from most to least likely to be the place the video is mainly " +
            "about. Return ONLY a JSON array (no markdown code fences, no explanation), where " +
            "each element is an object with exactly two fields: \"name\" (the place's short " +
            "name) and \"address\" (the fullest address you can determine - street, city, " +
            "region and country when known). " +
            "Example: [{\"name\":\"Eiffel Tower\",\"address\":\"Champ de Mars, 75007 Paris, France\"}]. " +
            "If you cannot identify any specific place from the text, return an empty JSON " +
            "array: []\n\n" +
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

    private fun parseLocations(responseText: String): List<ResolvedLocation> {
        val rawText =
            JSONObject(responseText)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()

        // Gemini sometimes wraps its JSON in a markdown code fence despite being told not to -
        // strip that defensively rather than fail the whole request over formatting.
        val json =
            rawText
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

        // Kept as an extra safety net for the legacy plain-text "UNKNOWN" sentinel from the
        // single-location prompt this replaced, in case a future prompt tweak drifts back to it.
        if (json.isEmpty() || json.equals("UNKNOWN", ignoreCase = true)) {
            throw AppError.NotFound("Gemini could not identify a specific place from the provided text")
        }

        val array = JSONArray(json)
        val locations =
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                val address = item.optString("address").trim().takeIf { it.isNotEmpty() }
                val query = if (address.isNullOrBlank()) name else "$name, $address"
                ResolvedLocation(name = name, address = address, destination = MapsDestination(query = query))
            }

        if (locations.isEmpty()) {
            throw AppError.NotFound("Gemini could not identify a specific place from the provided text")
        }
        return locations
    }

    private companion object {
        // "gemini-flash-latest" is a Google-maintained alias that always resolves to the current
        // stable Flash model, so this doesn't need to be bumped by hand every time Google retires
        // a pinned model version (e.g. gemini-1.5-flash was retired, breaking this integration).
        const val GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"

        /** Caps the text sent to Gemini to avoid excessively large requests. */
        const val MAX_INPUT_CHARS = 10_000
    }
}
