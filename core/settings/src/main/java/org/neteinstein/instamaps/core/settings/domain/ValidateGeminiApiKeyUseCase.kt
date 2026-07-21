package org.neteinstein.instamaps.core.settings.domain

/**
 * Checks a Gemini API key against the real Gemini API - see [GeminiApiKeyValidator] for what
 * [Result.success]/[Result.failure] mean here. Does not persist anything; saving is
 * [SaveGeminiApiKeyUseCase]'s job, deliberately kept separate so a caller can save a key
 * regardless of what this reports (see `feature:settings`'s `SettingsViewModel`).
 */
class ValidateGeminiApiKeyUseCase(
    private val validator: GeminiApiKeyValidator,
) {
    suspend operator fun invoke(apiKey: String): Result<ApiKeyValidity> = validator.validate(apiKey)
}
