package org.neteinstein.instamaps.feature.videoprocessing.domain

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import java.io.File

// Named distinctly from ExtractLocationCandidatesUseCaseTest's own private TestDispatcherProvider
// in this same package/file-set - Kotlin top-level classes clash across files by binary name
// regardless of `private`, even though `private` still scopes *access* to this file.
@OptIn(ExperimentalCoroutinesApi::class)
private class FakeDispatcherProvider(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
    override val unconfined = dispatcher
}

private class FakeVideoMetadataRepo(
    private val result: Result<String>,
) : VideoMetadataRepository {
    override suspend fun fetchDescription(url: String): Result<String> = result
}

private class FakeVideoDownloadRepo(
    private val result: Result<DownloadedVideo> = Result.success(DownloadedVideo(File("fake.mp4"))),
) : VideoDownloadRepository {
    override suspend fun download(url: String): Result<DownloadedVideo> = result

    override suspend fun delete(video: DownloadedVideo) = Unit
}

private class FakeFrameExtractorRepo(
    private val frames: List<VideoFrame>,
) : FrameExtractorRepository {
    override fun extractFrames(
        video: DownloadedVideo,
        intervalMs: Long,
    ) = flowOf(*frames.toTypedArray())
}

private class FakeTextRecognitionRepo(
    private val textByBitmap: Map<Bitmap, String>,
) : TextRecognitionRepository {
    override suspend fun recognizeText(bitmap: Bitmap): Result<String> = Result.success(textByBitmap[bitmap].orEmpty())
}

class CollectAllTextUseCaseTest {
    private fun useCaseFor(
        description: String = "",
        frameBitmapTextPairs: List<Pair<Bitmap, String>> = emptyList(),
        downloadResult: Result<DownloadedVideo> = Result.success(DownloadedVideo(File("fake.mp4"))),
    ): CollectAllTextUseCase =
        CollectAllTextUseCase(
            videoMetadataRepository = FakeVideoMetadataRepo(Result.success(description)),
            videoDownloadRepository = FakeVideoDownloadRepo(downloadResult),
            frameExtractorRepository =
                FakeFrameExtractorRepo(
                    frameBitmapTextPairs.mapIndexed { i, _ -> VideoFrame(i.toLong(), frameBitmapTextPairs[i].first) },
                ),
            textRecognitionRepository = FakeTextRecognitionRepo(frameBitmapTextPairs.toMap()),
            dispatcherProvider = FakeDispatcherProvider(),
        )

    @Test
    fun `emits CheckingDescription then Downloading then ExtractingFrames then Completed`() =
        runTest {
            val useCase = useCaseFor()

            val progress = useCase("https://example.com/video").toList()

            assertEquals(AllTextProgress.CheckingDescription, progress[0])
            assertEquals(AllTextProgress.Downloading, progress[1])
            assertEquals(AllTextProgress.ExtractingFrames, progress[2])
            assertTrue(progress.last() is AllTextProgress.Completed)
        }

    @Test
    fun `Completed texts includes the description when non-blank`() =
        runTest {
            val useCase = useCaseFor(description = "Amazing brunch spot")

            val progress = useCase("https://example.com/video").toList()

            val completed = progress.filterIsInstance<AllTextProgress.Completed>().single()
            assertTrue("Amazing brunch spot" in completed.texts)
        }

    @Test
    fun `Completed texts includes OCR text from frames`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val useCase = useCaseFor(frameBitmapTextPairs = listOf(bitmap to "Dishoom Shoreditch"))

            val progress = useCase("https://example.com/video").toList()

            val completed = progress.filterIsInstance<AllTextProgress.Completed>().single()
            assertTrue("Dishoom Shoreditch" in completed.texts)
        }

    @Test
    fun `Completed texts deduplicates identical strings`() =
        runTest {
            val bitmap1 = mock<Bitmap>()
            val bitmap2 = mock<Bitmap>()
            val useCase =
                useCaseFor(
                    frameBitmapTextPairs = listOf(bitmap1 to "Central Park", bitmap2 to "Central Park"),
                )

            val progress = useCase("https://example.com/video").toList()

            val completed = progress.filterIsInstance<AllTextProgress.Completed>().single()
            assertEquals(1, completed.texts.count { it == "Central Park" })
        }

    @Test
    fun `blank OCR results are not included in Completed texts`() =
        runTest {
            val bitmap = mock<Bitmap>()
            val useCase = useCaseFor(frameBitmapTextPairs = listOf(bitmap to "   "))

            val progress = useCase("https://example.com/video").toList()

            val completed = progress.filterIsInstance<AllTextProgress.Completed>().single()
            assertTrue(completed.texts.none { it.isBlank() })
        }

    @Test
    fun `emits Failed when the video download fails`() =
        runTest {
            val error = AppError.Network("download failed")
            val useCase = useCaseFor(downloadResult = Result.failure(error))

            val progress = useCase("https://example.com/video").toList()

            val failed = progress.filterIsInstance<AllTextProgress.Failed>().single()
            assertEquals(error, failed.error)
        }

    @Test
    fun `emits AnalyzingFrame for each processed frame`() =
        runTest {
            val bitmap1 = mock<Bitmap>()
            val bitmap2 = mock<Bitmap>()
            val useCase =
                useCaseFor(frameBitmapTextPairs = listOf(bitmap1 to "text1", bitmap2 to "text2"))

            val progress = useCase("https://example.com/video").toList()

            val frameEvents = progress.filterIsInstance<AllTextProgress.AnalyzingFrame>()
            assertEquals(2, frameEvents.size)
        }
}
