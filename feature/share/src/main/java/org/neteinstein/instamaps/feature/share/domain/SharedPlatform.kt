package org.neteinstein.instamaps.feature.share.domain

/**
 * The share sources this app explicitly recognizes. [UNKNOWN] is not rejected outright - the
 * on-device pipeline works from any URL yt-dlp itself can download, so a link from another site
 * can still work by extension; this value only drives which label the UI shows while processing.
 */
enum class SharedPlatform {
    INSTAGRAM,
    TIKTOK,
    UNKNOWN,
}

/**
 * The video URL pulled out of a share intent's raw text, plus which platform it came from.
 */
data class ParsedSharedUrl(
    val url: String,
    val platform: SharedPlatform,
)
