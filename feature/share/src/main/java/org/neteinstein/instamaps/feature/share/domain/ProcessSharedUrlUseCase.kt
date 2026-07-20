package org.neteinstein.instamaps.feature.share.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.neteinstein.instamaps.feature.geocoding.domain.ResolveLocationUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.AllTextProgress
import org.neteinstein.instamaps.feature.videoprocessing.domain.CollectAllTextUseCase

/**
 * Orchestrates the full share-to-maps-link pipeline using the Gemini API:
 *
 * 1. Fetches the shared video's caption/description (fast metadata-only fetch).
 * 2. Downloads the video and OCRs every extracted frame to collect all on-screen text.
 * 3. Sends the combined text (caption + all frame OCR) to [resolveLocationUseCase], which calls
 *    the Gemini 1.5 Flash API with the prompt:
 *    "these are caption and text from a video that talks about a specific place. from those
 *    determine the place and return Google maps location"
 * 4. Emits [ShareProcessingProgress.Found] with the resolved [MapsDestination] on success, or
 *    [ShareProcessingProgress.NotFound] / [ShareProcessingProgress.Failed] on failure.
 */
class ProcessSharedUrlUseCase(
    private val collectAllTextUseCase: CollectAllTextUseCase,
    private val resolveLocationUseCase: ResolveLocationUseCase,
) {
    operator fun invoke(url: String): Flow<ShareProcessingProgress> =
        flow {
            var failedError: Throwable? = null
            val allTexts = mutableListOf<String>()

            collectAllTextUseCase(url).collect { progress ->
                when (progress) {
                    is AllTextProgress.CheckingDescription -> emit(ShareProcessingProgress.CheckingDescription)
                    is AllTextProgress.Downloading -> emit(ShareProcessingProgress.Downloading)
                    is AllTextProgress.ExtractingFrames -> emit(ShareProcessingProgress.ExtractingFrames)
                    is AllTextProgress.AnalyzingFrame -> emit(ShareProcessingProgress.AnalyzingFrame(progress.frameIndex))
                    is AllTextProgress.Completed -> allTexts.addAll(progress.texts)
                    is AllTextProgress.Failed -> failedError = progress.error
                }
            }

            if (failedError != null) {
                emit(ShareProcessingProgress.Failed(failedError!!))
                return@flow
            }

            if (allTexts.isEmpty()) {
                emit(ShareProcessingProgress.NotFound("No text was found in this video"))
                return@flow
            }

            emit(ShareProcessingProgress.Geocoding)
            val combinedText = allTexts.joinToString("\n")
            resolveLocationUseCase(combinedText).fold(
                onSuccess = { destination ->
                    emit(
                        ShareProcessingProgress.Found(
                            destination = destination,
                            displayName = destination.query,
                        ),
                    )
                },
                onFailure = { error ->
                    emit(ShareProcessingProgress.NotFound("Could not identify the location: ${error.message}"))
                },
            )
        }
}
