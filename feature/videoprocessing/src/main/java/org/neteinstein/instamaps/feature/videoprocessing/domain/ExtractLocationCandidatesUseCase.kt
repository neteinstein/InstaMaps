package org.neteinstein.instamaps.feature.videoprocessing.domain

import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicReference

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
                val frameExtractionFailure = AtomicReference<Throwable?>(null)

                val producer =
                    launch(dispatcherProvider.io) {
                        try {
                            frameExtractorRepository.extractFrames(video).collect { frame ->
                                frameChannel.send(frame)
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            // A raw extractor failure (e.g. MediaMetadataRetriever choking on a
                            // corrupt/incompatible file) must not escape uncaught here: an
                            // exception thrown from this launch child would otherwise cancel the
                            // whole channelFlow instead of surfacing as a reportable
                            // VideoAnalysisProgress.Failed, unlike the download step above.
                            frameExtractionFailure.set(throwable)
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

                val extractionFailure = frameExtractionFailure.get()
                if (extractionFailure != null) {
                    send(VideoAnalysisProgress.Failed(extractionFailure))
                    return@channelFlow
                }

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
