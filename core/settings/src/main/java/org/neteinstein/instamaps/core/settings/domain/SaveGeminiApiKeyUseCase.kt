package org.neteinstein.instamaps.core.settings.domain

/**
 * Persists the Gemini API key entered on the Settings screen. Saving a blank value clears the
 * key (see [AppSettingsRepository.saveGeminiApiKey]), which is how the Settings screen supports
 * un-configuring InstaMaps again.
 */
class SaveGeminiApiKeyUseCase(
    private val repository: AppSettingsRepository,
) {
    suspend operator fun invoke(apiKey: String) {
        repository.saveGeminiApiKey(apiKey)
    }
}
