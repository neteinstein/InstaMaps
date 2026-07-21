package org.neteinstein.instamaps.core.settings.domain

/**
 * Whether a Gemini API key was actually accepted by the real Gemini API - see
 * [GeminiApiKeyValidator].
 */
enum class ApiKeyValidity {
    VALID,
    INVALID,
}

/**
 * Checks a Gemini API key against the real Gemini API, so the Settings screen can give the user
 * immediate feedback on a key they just entered (see `feature:settings`'s `SettingsViewModel`)
 * instead of only finding out the hard way the next time InstaMaps tries to resolve a location.
 */
interface GeminiApiKeyValidator {
    /**
     * [Result.success] carries [ApiKeyValidity.VALID]/[ApiKeyValidity.INVALID] once the Gemini
     * API has actually confirmed which one [apiKey] is. [Result.failure] means validity could
     * not be determined at all (e.g. no network) - callers must treat that as inconclusive,
     * neither valid nor invalid, rather than defaulting it to either one.
     */
    suspend fun validate(apiKey: String): Result<ApiKeyValidity>
}
