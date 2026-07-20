package org.neteinstein.instamaps.core.instagramauth.domain

/**
 * Discards a saved Instagram session. Called by `feature:share`'s `ProcessSharedUrlWorker` when
 * yt-dlp reports the session is no longer accepted, so the user is asked to log in again instead
 * of repeatedly retrying a dead cookie.
 */
class ClearInstagramSessionUseCase(
    private val repository: InstagramAuthRepository,
) {
    suspend operator fun invoke() {
        repository.clearSession()
    }
}
