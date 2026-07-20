package org.neteinstein.instamaps.core.settings.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether InstaMaps currently has a usable Gemini API key. Used to drive the "missing API key"
 * warning on `feature:share`'s main screen.
 */
class IsGeminiApiKeyConfiguredUseCase(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeGeminiApiKey().map { !it.isNullOrBlank() }
}
