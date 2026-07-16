package org.neteinstein.instamaps.feature.videoprocessing.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.neteinstein.instamaps.core.common.DispatcherProvider
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloads a shared video, then extracts location candidates from it, reporting progress as it
 * goes. This is `feature:share`'s fallback source of location candidates, tried only when
 * [ExtractLocationCandidatesFromDescriptionUseCase]'s much cheaper caption/description check comes
 * up empty - see `ProcessSharedUrlUseCase`, which runs that first, then this, resolving whichever
 * one's ranked candidates actually yields a real place.
 *
 * Implements the producer/consumer pipeline from the perf brief: a single producer coroutine
 * pulls frames out of [frameExtractorRepository] and pushes them onto a [Channel], while
 * [ocrConcurrency] consumer coroutines pull frames off that channel concurrently and run
 * OCR + [locationTextAnalyzer] on them. The extractor never blocks waiting for a frame's analysis
 * to finish before grabbing the next one.
 */
class ExtractLocationCandidatesUseCase(
    private val videoDownloadRepository: VideoDownloadRepository,
    private val frameExtractorRepository: FrameExtractorRepository,
    private val textRecognitionRepository: TextRecognitionRepository,
    private val locationTextAnalyzer: LocationTextAnalyzer,
    private val dispatcherProvider: DispatcherProvider,
    private val ocrConcurrency: Int = DEFAULT_OCR_CONCURRENCY,
) {
    operator fun invoke(url: String): Flow<VideoAnalysisProgress> =
        channelFlow {
            send(VideoAnalysisProgress.Downloading)
            val video =
                videoDownloadRepository.download(url).getOrElse {
                    send(VideoAnalysisProgress.Failed(it))
                    return@channelFlow
                }

            try {
                send(VideoAnalysisProgress.ExtractingFrames)

                val frameChannel = Channel<VideoFrame>(capacity = ocrConcurrency * 2)
                val candidates = mutableListOf<LocationCandidate>()
                val candidatesMutex = Mutex()
                val analyzedFrameCount = AtomicInteger(0)

                val producer =
                    launch(dispatcherProvider.io) {
                        try {
                            frameExtractorRepository.extractFrames(video).collect { frame ->
                                frameChannel.send(frame)
                            }
                        } finally {
                            frameChannel.close()
                        }
                    }

                val consumers =
                    List(ocrConcurrency) {
                        launch(dispatcherProvider.default) {
                            for (frame in frameChannel) {
                                val frameCandidates = analyzeFrame(frame)
                                candidatesMutex.withLock { candidates.addAll(frameCandidates) }
                                send(VideoAnalysisProgress.AnalyzingFrame(analyzedFrameCount.incrementAndGet()))
                            }
                        }
                    }

                producer.join()
                consumers.joinAll()

                send(VideoAnalysisProgress.Completed(candidates.sortedByDescending { it.confidence }))
            } finally {
                withContext(NonCancellable) {
                    videoDownloadRepository.delete(video)
                }
            }
        }

    private suspend fun analyzeFrame(frame: VideoFrame): List<LocationCandidate> =
        try {
            val text = textRecognitionRepository.recognizeText(frame.bitmap).getOrNull().orEmpty()
            locationTextAnalyzer.analyze(text)
        } finally {
            frame.bitmap.recycle()
        }

    private companion object {
        const val DEFAULT_OCR_CONCURRENCY = 3
    }
}
