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
 * goes. This is the "slow path" of the app - `feature:share`'s orchestrator only calls it when
 * the caption alone (run through the same [LocationTextParser]) wasn't enough.
 *
 * Implements the producer/consumer pipeline from the perf brief: a single producer coroutine
 * pulls frames out of [frameExtractorRepository] and pushes them onto a [Channel], while
 * [ocrConcurrency] consumer coroutines pull frames off that channel concurrently and run
 * OCR + entity extraction + text parsing on them. The extractor never blocks waiting for a frame's
 * analysis to finish before grabbing the next one.
 */
class ExtractLocationCandidatesUseCase(
    private val videoDownloadRepository: VideoDownloadRepository,
    private val frameExtractorRepository: FrameExtractorRepository,
    private val textRecognitionRepository: TextRecognitionRepository,
    private val entityExtractionRepository: EntityExtractionRepository,
    private val locationTextParser: LocationTextParser,
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
            if (text.isBlank()) {
                emptyList()
            } else {
                val addresses = entityExtractionRepository.extractAddresses(text).getOrNull().orEmpty()
                val addressCandidates = addresses.map { LocationCandidate.PlaceName(text = it, confidence = ADDRESS_CONFIDENCE) }
                locationTextParser.parse(text) + addressCandidates
            }
        } finally {
            frame.bitmap.recycle()
        }

    private companion object {
        const val DEFAULT_OCR_CONCURRENCY = 3
        const val ADDRESS_CONFIDENCE = 0.85f
    }
}
