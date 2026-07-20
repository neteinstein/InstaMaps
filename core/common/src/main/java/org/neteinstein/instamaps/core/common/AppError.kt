package org.neteinstein.instamaps.core.common

/**
 * Domain-level error hierarchy shared by every layer/module. Repository implementations
 * catch SDK/library-specific exceptions (Retrofit, youtubedl-android, ML Kit, Places SDK...)
 * and translate them into one of these so the presentation layer never needs to know about
 * a specific SDK's exception types.
 */
sealed class AppError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    class Network(message: String = "Network request failed", cause: Throwable? = null) : AppError(message, cause)

    class NotFound(message: String = "Nothing was found", cause: Throwable? = null) : AppError(message, cause)

    class InvalidInput(message: String, cause: Throwable? = null) : AppError(message, cause)

    class Parsing(message: String = "Failed to parse content", cause: Throwable? = null) : AppError(message, cause)

    class MissingConfiguration(message: String, cause: Throwable? = null) : AppError(message, cause)

    class PlatformUnavailable(message: String, cause: Throwable? = null) : AppError(message, cause)

    /**
     * A request needs an authenticated session that's either missing or no longer accepted -
     * currently only raised by `feature:videoprocessing`'s yt-dlp download when Instagram demands
     * a login (see `YtDlpVideoDownloadRepository`). Kept distinct from [Network] so callers (e.g.
     * `feature:share`'s `ProcessSharedUrlWorker`) can route the user back to the Instagram login
     * screen instead of showing a generic error.
     */
    class AuthenticationRequired(message: String, cause: Throwable? = null) : AppError(message, cause)

    class Unknown(message: String = "Something went wrong", cause: Throwable? = null) : AppError(message, cause)
}

/**
 * Builds a message from [prefix] plus this throwable's own detail, when it has one. Without this,
 * every repository-level failure collapses to the same fixed [prefix] (e.g. every yt-dlp failure
 * showing "Video download failed" whether the real cause was an unsupported URL, a network
 * timeout, or Instagram requiring a login), which makes both on-screen troubleshooting and bug
 * reports useless. Takes the last non-blank line of [Throwable.message] rather than the whole
 * thing, since some SDKs (yt-dlp's captured subprocess stderr in particular) report multi-line
 * output where only the final line is the actual error summary. Falls back to [prefix] alone when
 * there is no usable message.
 */
fun Throwable.describeOrDefault(prefix: String): String {
    val detail = message?.lineSequence()?.map { it.trim() }?.lastOrNull { it.isNotEmpty() }
    return if (detail.isNullOrEmpty()) prefix else "$prefix: $detail"
}
