package org.neteinstein.instamaps.feature.videoprocessing.data

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.withContext
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.core.common.describeOrDefault
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoMetadataRepository

/**
 * Fetches a shared video's caption/description via yt-dlp's metadata-only mode
 * ([YoutubeDL.getInfo], i.e. `--dump-json`) - no media is downloaded, so this is far cheaper than
 * [YtDlpVideoDownloadRepository.download], which is exactly why
 * [org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesFromDescriptionUseCase]
 * tries it first.
 *
 * Falls back to [com.yausername.youtubedl_android.mapper.VideoInfo.fulltitle]/`title` when
 * `description` is blank: decompiling the real `library:0.18.1` artifact confirms not every
 * extractor populates `description` (its `VideoInfo` mapper leaves it null in that case), and a
 * caption baked into the title is still worth parsing rather than treating as no signal at all.
 *
 * Keeps its own [initialized] flag rather than sharing [YtDlpVideoDownloadRepository]'s: both
 * ultimately call the same idempotent [YoutubeDL.init]/[FFmpeg.init] (each a no-op after the very
 * first real call across the whole process, per their own internal `initialized` guards), so
 * there's no real duplicated work, only a redundant no-op check the first time each repository
 * instance is used.
 */
class YtDlpVideoMetadataRepository(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : VideoMetadataRepository {
    private var initialized = false

    override suspend fun fetchDescription(url: String): Result<String> =
        safeCall(mapError = { AppError.Network(it.describeOrDefault("Fetching video metadata failed"), it) }) {
            withContext(dispatcherProvider.io) {
                ensureInitialized()
                val info = YoutubeDL.getInfo(url)
                info.description?.takeIf { it.isNotBlank() }
                    ?: info.fulltitle?.takeIf { it.isNotBlank() }
                    ?: info.title.orEmpty()
            }
        }

    private fun ensureInitialized() {
        if (initialized) return
        YoutubeDL.init(context)
        FFmpeg.init(context)
        initialized = true
    }
}
