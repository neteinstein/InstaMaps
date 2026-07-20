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
 * Collects ALL text visible in a shared video: the video's caption/description (via a cheap
 * metadata-only fetch) plus the raw OCR output from every extracted frame. No location heuristics
 * are applied - the raw text is handed directly to `feature:geocoding`'s
 * `ResolveLocationUseCase`, which sends it to the Gemini API to identify the place.
 *
 * Implements the same producer/consumer frame pipeline as [ExtractLocationCandidatesUseCase]:
 * a single producer coroutine pushes frames onto a [Channel] while [ocrConcurrency] consumer
 * coroutines pull frames off and run OCR concurrently, so frame extraction never blocks on OCR.
 */
class CollectAllTextUseCase(
    private val videoMetadataRepository: VideoMetadataRepository,
    private val videoDownloadRepository: VideoDownloadRepository,
    private val frameExtractorRepository: FrameExtractorRepository,
    private val textRecognitionRepository: TextRecognitionRepository,
    private val dispatcherProvider: DispatcherProvider,
    private val ocrConcurrency: Int = DEFAULT_OCR_CONCURRENCY,
) {
    operator fun invoke(url: String): Flow<AllTextProgress> =
        channelFlow {
            send(AllTextProgress.CheckingDescription)
            val description = videoMetadataRepository.fetchDescription(url).getOrNull().orEmpty()

            send(AllTextProgress.Downloading)
            val video =
                videoDownloadRepository.download(url).getOrElse {
                    send(AllTextProgress.Failed(it))
                    return@channelFlow
                }

            try {
                send(AllTextProgress.ExtractingFrames)

                val frameChannel = Channel<VideoFrame>(capacity = ocrConcurrency * 2)
                val texts = mutableListOf<String>()
                val textsMutex = Mutex()
                val analyzedFrameCount = AtomicInteger(0)
                val frameExtractionFailure = AtomicReference<Throwable?>(null)

                if (description.isNotBlank()) {
                    textsMutex.withLock { texts.add(description) }
                }

                val producer =
                    launch(dispatcherProvider.io) {
                        try {
                            frameExtractorRepository.extractFrames(video).collect { frame ->
                                frameChannel.send(frame)
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            frameExtractionFailure.set(throwable)
                        } finally {
                            frameChannel.close()
                        }
                    }

                val consumers =
                    List(ocrConcurrency) {
                        launch(dispatcherProvider.default) {
                            for (frame in frameChannel) {
                                val text = recognizeFrameText(frame)
                                if (text.isNotBlank()) textsMutex.withLock { texts.add(text) }
                                send(AllTextProgress.AnalyzingFrame(analyzedFrameCount.incrementAndGet()))
                            }
                        }
                    }

                producer.join()
                consumers.joinAll()

                val extractionFailure = frameExtractionFailure.get()
                if (extractionFailure != null) {
                    send(AllTextProgress.Failed(extractionFailure))
                    return@channelFlow
                }

                send(AllTextProgress.Completed(texts.distinct()))
            } finally {
                withContext(NonCancellable) {
                    videoDownloadRepository.delete(video)
                }
            }
        }

    private suspend fun recognizeFrameText(frame: VideoFrame): String =
        try {
            textRecognitionRepository.recognizeText(frame.bitmap).getOrNull().orEmpty()
        } finally {
            frame.bitmap.recycle()
        }

    private companion object {
        const val DEFAULT_OCR_CONCURRENCY = 3
    }
}
