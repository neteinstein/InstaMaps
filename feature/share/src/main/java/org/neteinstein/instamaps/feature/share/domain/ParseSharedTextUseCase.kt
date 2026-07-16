package org.neteinstein.instamaps.feature.share.domain

import java.net.URI

/**
 * Pulls the actual video URL out of the raw text Android hands over from an `ACTION_SEND` intent.
 * Instagram/TikTok's share sheet prepends a line like "Check out this video!" before the link, so
 * the whole `EXTRA_TEXT` string can't be used as a URL as-is - only the URL substring matters.
 *
 * Kept as a plain string/regex parser (no `android.net.Uri`) so it is exercisable by pure JUnit
 * tests, matching the same convention as [org.neteinstein.instamaps.feature.maps.domain.BuildMapsDeepLinkUseCase].
 */
class ParseSharedTextUseCase {
    operator fun invoke(sharedText: String): ParsedSharedUrl? {
        val rawMatch = URL_PATTERN.find(sharedText)?.value ?: return null
        // A shared link is often embedded in a sentence ("Check this out! https://...reel/abc.")
        // rather than standalone, so trailing punctuation swept up by the greedy match needs to be
        // stripped before it's treated as part of the URL.
        val url = rawMatch.trimEnd('.', ',', '!', '?', ')', ']', '}', '"', '\'')
        if (url.isBlank()) return null
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase()
        return ParsedSharedUrl(url = url, platform = platformFor(host))
    }

    private fun platformFor(host: String): SharedPlatform =
        when {
            INSTAGRAM_HOSTS.any { host == it || host.endsWith(".$it") } -> SharedPlatform.INSTAGRAM
            TIKTOK_HOSTS.any { host == it || host.endsWith(".$it") } -> SharedPlatform.TIKTOK
            else -> SharedPlatform.UNKNOWN
        }

    private companion object {
        val URL_PATTERN = Regex("https?://\\S+")
        val INSTAGRAM_HOSTS = setOf("instagram.com", "instagr.am")
        val TIKTOK_HOSTS = setOf("tiktok.com")
    }
}
