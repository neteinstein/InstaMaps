package org.neteinstein.instamaps.feature.videoprocessing.data

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.withContext
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.feature.videoprocessing.domain.DownloadedVideo
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoDownloadRepository
import java.io.File
import java.util.UUID

/**
 * Downloads a video with yt-dlp (via the youtubedl-android wrapper), capped at 480p per the perf
 * brief: `-f "bestvideo[height<=480]+bestaudio/best[height<=480]"`. This both shrinks the download
 * itself (~15MB -> ~2MB) and speeds up keyframe decoding later in
 * [MediaMetadataRetrieverFrameExtractor], since lower-resolution sources use simpler encoding
 * profiles - most of the end-to-end latency win happens here, before extraction even starts.
 *
 * [YoutubeDL.init]/[FFmpeg.init] are idempotent internally but do real file-extraction work the
 * first time, so initialization is deferred to the first real download rather than app startup:
 * many shares are resolved by the caption fast path and never need this GPL-licensed download
 * path at all.
 */
class YtDlpVideoDownloadRepository(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : VideoDownloadRepository {
    private var initialized = false

    override suspend fun download(url: String): Result<DownloadedVideo> =
        safeCall(mapError = { AppError.Network("Video download failed", it) }) {
            withContext(dispatcherProvider.io) {
                ensureInitialized()

                val outputDir = File(context.cacheDir, "$VIDEO_CACHE_DIR/${UUID.randomUUID()}")
                outputDir.mkdirs()

                val request =
                    YoutubeDLRequest(url)
                        .addOption("-f", MAX_480P_FORMAT)
                        .addOption("-o", File(outputDir, "video.%(ext)s").absolutePath)

                YoutubeDL.execute(request = request, processId = null, redirectErrorStream = false, callback = null)

                val downloadedFile =
                    outputDir.listFiles()?.firstOrNull()
                        ?: error("yt-dlp reported success but produced no file in $outputDir")

                DownloadedVideo(file = downloadedFile)
            }
        }

    override suspend fun delete(video: DownloadedVideo) {
        withContext(dispatcherProvider.io) {
            video.file.parentFile?.deleteRecursively()
        }
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
    }
}
