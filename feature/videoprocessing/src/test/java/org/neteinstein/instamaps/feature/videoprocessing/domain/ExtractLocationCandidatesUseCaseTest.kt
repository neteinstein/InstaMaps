package org.neteinstein.instamaps.feature.videoprocessing.domain

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
private class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
    override val unconfined = dispatcher
}

private class FakeVideoDownloadRepository(
    private val result: Result<DownloadedVideo> = Result.success(DownloadedVideo(File("fake-video.mp4"))),
) : VideoDownloadRepository {
    var deletedVideo: DownloadedVideo? = null

    override suspend fun download(url: String): Result<DownloadedVideo> = result

    override suspend fun delete(video: DownloadedVideo) {
        deletedVideo = video
    }
}

private class FakeFrameExtractorRepository(
    private val frames: List<VideoFrame>,
) : FrameExtractorRepository {
    override fun extractFrames(
        video: DownloadedVideo,
        intervalMs: Long,
    ) = flowOf(*frames.toTypedArray())
}

private class FakeTextRecognitionRepository(
    private val textByBitmap: Map<Bitmap, String>,
) : TextRecognitionRepository {
    override suspend fun recognizeText(bitmap: Bitmap): Result<String> = Result.success(textByBitmap[bitmap].orEmpty())
}

private class FakeEntityExtractionRepository(
    private val addresses: List<String> = emptyList(),
) : EntityExtractionRepository {
    override suspend fun extractAddresses(text: String): Result<List<String>> = Result.success(addresses)
}

class ExtractLocationCandidatesUseCaseTest {
    @Test
    fun `emits Downloading then ExtractingFrames then a Completed with ranked candidates`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val frame = VideoFrame(timestampMs = 0L, bitmap = bitmap)
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = FakeVideoDownloadRepository(),
                    frameExtractorRepository = FakeFrameExtractorRepository(listOf(frame)),
                    textRecognitionRepository = FakeTextRecognitionRepository(mapOf(bitmap to "📍 Eiffel Tower, Paris")),
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertEquals(VideoAnalysisProgress.Downloading, progress.first())
            assertTrue(progress.contains(VideoAnalysisProgress.ExtractingFrames))
            val completed = progress.filterIsInstance<VideoAnalysisProgress.Completed>().single()
            val topCandidate = completed.candidates.first() as LocationCandidate.PlaceName
            assertEquals("Eiffel Tower, Paris", topCandidate.text)
        }

    @Test
    fun `merges OCR text candidates with entity-extracted address candidates`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val frame = VideoFrame(timestampMs = 0L, bitmap = bitmap)
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = FakeVideoDownloadRepository(),
                    frameExtractorRepository = FakeFrameExtractorRepository(listOf(frame)),
                    textRecognitionRepository = FakeTextRecognitionRepository(mapOf(bitmap to "some storefront sign")),
                    entityExtractionRepository = FakeEntityExtractionRepository(listOf("221B Baker Street, London")),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val completed = progress.filterIsInstance<VideoAnalysisProgress.Completed>().single()
            val addressCandidate = completed.candidates.filterIsInstance<LocationCandidate.PlaceName>().single()
            assertEquals("221B Baker Street, London", addressCandidate.text)
        }

    @Test
    fun `recycles every frame bitmap after analysis`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = FakeVideoDownloadRepository(),
                    frameExtractorRepository = FakeFrameExtractorRepository(listOf(VideoFrame(0L, bitmap))),
                    textRecognitionRepository = FakeTextRecognitionRepository(emptyMap()),
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            useCase("https://instagram.com/reel/abc").toList()

            verify(bitmap).recycle()
        }

    @Test
    fun `deletes the downloaded video after analysis completes`() =
        runTest {
            val repository = FakeVideoDownloadRepository()
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = repository,
                    frameExtractorRepository = FakeFrameExtractorRepository(emptyList()),
                    textRecognitionRepository = FakeTextRecognitionRepository(emptyMap()),
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            useCase("https://instagram.com/reel/abc").toList()

            assertNotNull(repository.deletedVideo)
        }

    @Test
    fun `emits only Downloading then Failed when the download fails`() =
        runTest {
            val error = AppError.Network("boom")
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = FakeVideoDownloadRepository(Result.failure(error)),
                    frameExtractorRepository = FakeFrameExtractorRepository(emptyList()),
                    textRecognitionRepository = FakeTextRecognitionRepository(emptyMap()),
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertEquals(listOf(VideoAnalysisProgress.Downloading, VideoAnalysisProgress.Failed(error)), progress)
        }

    @Test
    fun `completes with no candidates when no frame has recognizable text`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val useCase =
                ExtractLocationCandidatesUseCase(
                    videoDownloadRepository = FakeVideoDownloadRepository(),
                    frameExtractorRepository = FakeFrameExtractorRepository(listOf(VideoFrame(0L, bitmap))),
                    textRecognitionRepository = FakeTextRecognitionRepository(emptyMap()),
                    entityExtractionRepository = FakeEntityExtractionRepository(),
                    locationTextParser = LocationTextParser(),
                    dispatcherProvider = TestDispatcherProvider(),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val completed = progress.filterIsInstance<VideoAnalysisProgress.Completed>().single()
            assertTrue(completed.candidates.isEmpty())
        }
}
