package org.neteinstein.instamaps.core.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * Observes the Gemini API key currently saved in Settings, so callers (the Settings screen
 * itself, and anything gating on "is InstaMaps configured", such as `feature:share`'s main
 * screen warnings) don't need to depend on [AppSettingsRepository] directly.
 */
class ObserveGeminiApiKeyUseCase(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke(): Flow<String?> = repository.observeGeminiApiKey()
}
