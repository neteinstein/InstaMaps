package org.neteinstein.instamaps.feature.videoprocessing.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.AppError

/**
 * Exercises the pure, SDK-independent helpers in `YtDlpVideoDownloadRepository.kt` - the
 * repository class itself is deliberately untested SDK glue (see `agents.md`'s Testing
 * Standards), matching the precedent of other yt-dlp/`MediaMetadataRetriever`-backed classes in
 * this module.
 */
class YtDlpVideoDownloadRepositoryFunctionsTest {
    @Test
    fun `buildNetscapeCookieFileContent converts a browser cookie header into Netscape lines`() {
        val content = buildNetscapeCookieFileContent("sessionid=abc123; csrftoken=xyz789")

        val lines = content.lines()
        assertEquals("# Netscape HTTP Cookie File", lines[0])
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t2147483647\tsessionid\tabc123", lines[1])
        assertEquals(".instagram.com\tTRUE\t/\tTRUE\t2147483647\tcsrftoken\txyz789", lines[2])
    }

    @Test
    fun `buildNetscapeCookieFileContent keeps everything after the first equals as the value`() {
        val content = buildNetscapeCookieFileContent("sessionid=abc==123==")

        assertTrue(content.lines()[1].endsWith("\tsessionid\tabc==123=="))
    }

    @Test
    fun `buildNetscapeCookieFileContent skips malformed pairs without an equals sign`() {
        val content = buildNetscapeCookieFileContent("sessionid=abc123; garbage; csrftoken=xyz789")

        assertEquals(3, content.lines().size)
        assertTrue(content.lines()[1].contains("sessionid"))
        assertTrue(content.lines()[2].contains("csrftoken"))
    }

    @Test
    fun `ytDlpErrorToAppError maps a login-required stderr message to AuthenticationRequired for an Instagram URL`() {
        val error =
            ytDlpErrorToAppError(
                "https://www.instagram.com/reel/abc123/",
                RuntimeException("ERROR: [Instagram] Requested content is not available, login required"),
            )

        assertTrue(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError maps Instagram's real logged-in message to AuthenticationRequired`() {
        val error =
            ytDlpErrorToAppError(
                "https://instagram.com/p/xyz789/",
                RuntimeException(
                    "Instagram sent an empty media response. Check if this post is accessible in your " +
                        "browser without being logged-in.",
                ),
            )

        assertTrue(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError maps a rate-limit stderr message to AuthenticationRequired for an Instagram URL`() {
        val error =
            ytDlpErrorToAppError(
                "https://www.instagram.com/reel/abc123/",
                RuntimeException("ERROR: [Instagram] rate-limit reached"),
            )

        assertTrue(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError is case-insensitive`() {
        val error = ytDlpErrorToAppError("https://www.instagram.com/reel/abc123/", RuntimeException("ERROR: LOGIN REQUIRED"))

        assertTrue(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError matches instagr am short host and subdomains too`() {
        val error = ytDlpErrorToAppError("https://instagr.am/p/abc123/", RuntimeException("ERROR: login required"))

        assertTrue(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError falls back to Network for unrelated failures`() {
        val error =
            ytDlpErrorToAppError(
                "https://www.instagram.com/reel/abc123/",
                RuntimeException("ERROR: Unable to download webpage: timed out"),
            )

        assertTrue(error is AppError.Network)
        assertFalse(error is AppError.AuthenticationRequired)
    }

    @Test
    fun `ytDlpErrorToAppError falls back to Network when there is no message`() {
        val error = ytDlpErrorToAppError("https://www.instagram.com/reel/abc123/", RuntimeException())

        assertTrue(error is AppError.Network)
    }

    @Test
    fun `ytDlpErrorToAppError never classifies a non-Instagram URL as AuthenticationRequired, even with matching wording`() {
        val error = ytDlpErrorToAppError("https://www.tiktok.com/@user/video/123", RuntimeException("ERROR: login required"))

        assertTrue(error is AppError.Network)
        assertFalse(error is AppError.AuthenticationRequired)
    }
}
