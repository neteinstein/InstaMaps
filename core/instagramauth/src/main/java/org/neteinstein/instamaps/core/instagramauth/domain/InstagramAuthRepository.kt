package org.neteinstein.instamaps.core.instagramauth.domain

import kotlinx.coroutines.flow.Flow

/**
 * Persists the Instagram session captured by the WebView login screen (see
 * `feature:instagramauth`). InstaMaps never sees the user's Instagram password - only the
 * `Cookie` header produced by a successful browser login, which `feature:videoprocessing`'s
 * yt-dlp download attaches to Instagram requests so they're less likely to be rejected as
 * logged-out/rate-limited (see `YtDlpVideoDownloadRepository`).
 */
interface InstagramAuthRepository {
    /** Emits `true` once a session has been saved, `false` if none exists or it was cleared. */
    fun observeIsAuthenticated(): Flow<Boolean>

    /** The raw `Cookie` header to attach to Instagram requests, or `null` if not authenticated. */
    suspend fun getCookieHeader(): String?

    /** Stores [cookieHeader] (e.g. `"sessionid=...; csrftoken=...; ds_user_id=..."`), encrypted at rest. */
    suspend fun saveSession(cookieHeader: String)

    /** Discards any saved session, e.g. after yt-dlp reports the session is no longer accepted. */
    suspend fun clearSession()
}
