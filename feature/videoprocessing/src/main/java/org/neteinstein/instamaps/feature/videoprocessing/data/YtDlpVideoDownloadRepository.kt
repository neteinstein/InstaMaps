package org.neteinstein.instamaps.feature.videoprocessing.data

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.withContext
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.core.instagramauth.domain.InstagramAuthRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.DownloadedVideo
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoDownloadRepository
import java.io.File
import java.net.URI
import java.util.UUID

/**
 * Downloads a video with yt-dlp (via the youtubedl-android wrapper), capped at 480p per the perf
 * brief: `-f "bestvideo[height<=480]+bestaudio/best[height<=480]"`. This both shrinks the download
 * itself (~15MB -> ~2MB) and speeds up keyframe decoding later in
 * [MediaMetadataRetrieverFrameExtractor], since lower-resolution sources use simpler encoding
 * profiles - most of the end-to-end latency win happens here, before extraction even starts.
 *
 * [YoutubeDL.init]/[FFmpeg.init] are idempotent internally but do real file-extraction work the
 * first time, so initialization is deferred to the first real download rather than app startup,
 * keeping cold-start latency down for a component that only runs inside the background worker.
 *
 * Reaches into [instagramAuthRepository] directly (not through a use case) to attach a saved
 * Instagram session to every download, the same "data layer reads a cross-cutting core
 * dependency directly" shape `feature:geocoding`'s `PlacesSdkPlaceSearchRepository` already uses
 * for the Places API key. The cookie file is domain-scoped (see [buildNetscapeCookieFileContent]),
 * so attaching it unconditionally is a harmless no-op for TikTok/other URLs - yt-dlp only sends a
 * cookie to a request whose host matches the cookie's domain.
 */
class YtDlpVideoDownloadRepository(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val instagramAuthRepository: InstagramAuthRepository,
) : VideoDownloadRepository {
    private var initialized = false

    override suspend fun download(url: String): Result<DownloadedVideo> =
        safeCall(mapError = { throwable -> ytDlpErrorToAppError(url, throwable) }) {
            withContext(dispatcherProvider.io) {
                ensureInitialized()

                val outputDir = File(context.cacheDir, "$VIDEO_CACHE_DIR/${UUID.randomUUID()}")
                outputDir.mkdirs()

                val request =
                    YoutubeDLRequest(url)
                        .addOption("-f", MAX_480P_FORMAT)
                        .addOption("-o", File(outputDir, "video.%(ext)s").absolutePath)

                attachInstagramCookiesIfAvailable(request, outputDir)

                YoutubeDL.execute(request = request, processId = null, redirectErrorStream = false, callback = null)

                val downloadedFile =
                    outputDir.listFiles { file -> file.name != COOKIE_FILE_NAME }?.firstOrNull()
                        ?: error("yt-dlp reported success but produced no file in $outputDir")

                DownloadedVideo(file = downloadedFile)
            }
        }

    override suspend fun delete(video: DownloadedVideo) {
        withContext(dispatcherProvider.io) {
            video.file.parentFile?.deleteRecursively()
        }
    }

    /**
     * Writes a Netscape-format cookie file (yt-dlp's `--cookies` expects that format) into the
     * per-download [outputDir], which [delete] already removes recursively - no separate cleanup
     * needed. A missing session (never logged in, or [instagramAuthRepository] was cleared after
     * a previous auth failure) just means the request goes out unauthenticated, exactly like
     * today's behavior.
     */
    private suspend fun attachInstagramCookiesIfAvailable(
        request: YoutubeDLRequest,
        outputDir: File,
    ) {
        val cookieHeader = instagramAuthRepository.getCookieHeader() ?: return
        val cookieFile = File(outputDir, COOKIE_FILE_NAME)
        cookieFile.writeText(buildNetscapeCookieFileContent(cookieHeader))
        request.addOption("--cookies", cookieFile.absolutePath)
    }

    private fun ensureInitialized() {
        if (initialized) return
        YoutubeDL.init(context)
        FFmpeg.init(context)
        initialized = true
    }

    private companion object {
        const val VIDEO_CACHE_DIR = "videoprocessing/downloads"
        const val MAX_480P_FORMAT = "bestvideo[height<=480]+bestaudio/best[height<=480]"
        const val COOKIE_FILE_NAME = "cookies.txt"
    }
}

private const val INSTAGRAM_COOKIE_DOMAIN = ".instagram.com"

// CookieManager.getCookie() doesn't expose the cookies' real expiry, so this uses the same
// far-future placeholder browser cookie-export tools conventionally write (the max signed 32-bit
// Unix timestamp) - yt-dlp only cares that it's in the future.
private const val FAR_FUTURE_EXPIRY = "2147483647"

/**
 * Converts a browser-style `Cookie` header (`"name=value; name2=value2"`, as produced by
 * `android.webkit.CookieManager.getCookie()` in `feature:instagramauth`'s login screen) into the
 * Netscape cookie-file format yt-dlp's `--cookies` option expects: one TAB-separated line per
 * cookie of `domain  includeSubdomains  path  secure  expiry  name  value`. Splits each pair on
 * its *first* `=` only, since a cookie value (e.g. a base64-ish session id) can itself contain
 * `=` characters.
 */
fun buildNetscapeCookieFileContent(cookieHeader: String): String {
    val cookieLines =
        cookieHeader.split(";").mapNotNull { pair ->
            val trimmed = pair.trim()
            val separatorIndex = trimmed.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null

            val name = trimmed.substring(0, separatorIndex)
            val value = trimmed.substring(separatorIndex + 1)
            listOf(INSTAGRAM_COOKIE_DOMAIN, "TRUE", "/", "TRUE", FAR_FUTURE_EXPIRY, name, value).joinToString("\t")
        }
    return (listOf("# Netscape HTTP Cookie File") + cookieLines).joinToString("\n")
}

// Verified against yt-dlp's real `yt_dlp/extractor/instagram.py` source (e.g. its "empty media
// response... without being logged-in" message, and the generic login-required helper's
// "cookies for the authentication" wording) rather than assumed - see the module's `agents.md`
// policy of checking real third-party API/error shapes before coding against them. Kept as a
// list of resilient, lowercase substrings (not one exact string) since the yt-dlp Python binary
// youtubedl-android bundles/updates independently of this app's release cadence, so exact wording
// can drift between versions.
private val AUTH_ERROR_SIGNATURES =
    listOf(
        "logged-in",
        "logged in",
        "login required",
        "login_required",
        "rate-limit",
        "checkpoint_required",
        "cookies for the authentication",
    )

/**
 * Classifies a yt-dlp failure ([throwable]'s message is yt-dlp's raw stderr output - see
 * `YoutubeDLException`) as [AppError.AuthenticationRequired] when it looks like Instagram is
 * demanding a (re-)login, falling back to a generic [AppError.Network] otherwise.
 *
 * Only ever classifies as [AppError.AuthenticationRequired] when [url] itself is an Instagram
 * link - the [AUTH_ERROR_SIGNATURES] substrings (e.g. "login required", "rate-limit") are generic
 * enough wording that other extractors (TikTok's included) could plausibly emit similar text for
 * their own login/rate-limit errors, which would otherwise wrongly route a TikTok failure into the
 * Instagram login screen. This mirrors `feature:share`'s `ParseSharedTextUseCase` host-matching so
 * the "Instagram-only gating, TikTok unaffected" rule holds at every layer, not just the UI's.
 */
fun ytDlpErrorToAppError(
    url: String,
    throwable: Throwable,
): AppError {
    val message = throwable.message?.lowercase().orEmpty()
    return if (isInstagramUrl(url) && AUTH_ERROR_SIGNATURES.any { message.contains(it) }) {
        AppError.AuthenticationRequired("Instagram is asking to log in again", throwable)
    } else {
        AppError.Network("Video download failed", throwable)
    }
}

private fun isInstagramUrl(url: String): Boolean {
    val host = runCatching { URI(url).host }.getOrNull().orEmpty().lowercase()
    return INSTAGRAM_URL_HOSTS.any { host == it || host.endsWith(".$it") }
}

private val INSTAGRAM_URL_HOSTS = setOf("instagram.com", "instagr.am")
