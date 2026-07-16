package org.neteinstein.instamaps.feature.videoprocessing.data

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.feature.videoprocessing.domain.DownloadedVideo
import org.neteinstein.instamaps.feature.videoprocessing.domain.FrameExtractorRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoFrame

/**
 * Implements the three [MediaMetadataRetriever]-side perf optimizations from the brief:
 * 1. [MediaMetadataRetriever.OPTION_CLOSEST_SYNC] - snap to the nearest keyframe instead of
 *    decoding every P/B-frame up to the exact timestamp.
 * 2. [MediaMetadataRetriever.getScaledFrameAtTime] (API 27+, matches this app's minSdk) -
 *    downscale during the native decode step itself, so a full-resolution [android.graphics.Bitmap]
 *    never touches the JVM heap and triggers a GC pause.
 * 3. Emits frames lazily via a cold [Flow] - the producer side of the Channel pipeline built in
 *    [org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesUseCase] -
 *    so OCR can start on frame 1 while frame 2 is still decoding.
 */
class MediaMetadataRetrieverFrameExtractor(
    private val dispatcherProvider: DispatcherProvider,
) : FrameExtractorRepository {
    override fun extractFrames(
        video: DownloadedVideo,
        intervalMs: Long,
    ): Flow<VideoFrame> =
        flow {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(video.file.absolutePath)
                val durationMs =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

                if (durationMs <= 0L) {
                    // Duration couldn't be determined (corrupt/unusual container) - still try a
                    // single frame at the start rather than giving up on the video entirely.
                    emitFrameAt(retriever, timestampMs = 0L)
                } else {
                    var timestampMs = 0L
                    var framesEmitted = 0
                    while (timestampMs < durationMs && framesEmitted < MAX_FRAMES) {
                        emitFrameAt(retriever, timestampMs)
                        framesEmitted++
                        timestampMs += intervalMs
                    }
                }
            } finally {
                retriever.release()
            }
        }.flowOn(dispatcherProvider.io)

    private suspend fun FlowCollector<VideoFrame>.emitFrameAt(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
    ) {
        val bitmap =
            retriever.getScaledFrameAtTime(
                timestampMs * MICROS_PER_MILLI,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                FRAME_DIMENSION_PX,
                FRAME_DIMENSION_PX,
            )
        if (bitmap != null) {
            emit(VideoFrame(timestampMs = timestampMs, bitmap = bitmap))
        }
    }

    private companion object {
        const val FRAME_DIMENSION_PX = 640
        const val MAX_FRAMES = 30
        const val MICROS_PER_MILLI = 1_000L
    }
}
