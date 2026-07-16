package org.neteinstein.instamaps.core.settings.domain

/**
 * Persists the Places API key entered on the Settings screen. Saving a blank value clears the
 * key (see [AppSettingsRepository.savePlacesApiKey]), which is how the Settings screen supports
 * un-configuring InstaMaps again.
 */
class SavePlacesApiKeyUseCase(
    private val repository: AppSettingsRepository,
) {
    suspend operator fun invoke(apiKey: String) {
        repository.savePlacesApiKey(apiKey)
    }
}
