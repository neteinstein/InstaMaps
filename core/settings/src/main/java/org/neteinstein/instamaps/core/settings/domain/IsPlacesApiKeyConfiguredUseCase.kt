package org.neteinstein.instamaps.core.settings.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether InstaMaps currently has a usable Places API key. Used to drive the "missing API key"
 * warning on `feature:share`'s main screen.
 */
class IsPlacesApiKeyConfiguredUseCase(
    private val repository: AppSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observePlacesApiKey().map { !it.isNullOrBlank() }
}
