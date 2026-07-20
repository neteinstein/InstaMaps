package org.neteinstein.instamaps.core.instagramauth.domain

/**
 * Persists the session cookie captured right after a successful login on the WebView login
 * screen (see `feature:instagramauth`'s `InstagramLoginViewModel`).
 */
class SaveInstagramSessionUseCase(
    private val repository: InstagramAuthRepository,
) {
    suspend operator fun invoke(cookieHeader: String) {
        repository.saveSession(cookieHeader)
    }
}
