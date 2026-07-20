package org.neteinstein.instamaps.core.instagramauth.domain

import kotlinx.coroutines.flow.Flow

/**
 * Observes whether InstaMaps currently has a usable Instagram session. Used to drive the
 * "Connect Instagram" warning on `feature:share`'s main screen and to gate/auto-resume Instagram
 * shares once the user logs in.
 */
class ObserveInstagramAuthStateUseCase(
    private val repository: InstagramAuthRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeIsAuthenticated()
}
