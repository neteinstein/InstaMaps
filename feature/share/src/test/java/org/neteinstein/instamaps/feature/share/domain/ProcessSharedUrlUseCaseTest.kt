package org.neteinstein.instamaps.feature.share.domain

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.DispatcherProvider
import org.neteinstein.instamaps.feature.geocoding.domain.LocationRepository
import org.neteinstein.instamaps.feature.geocoding.domain.ResolveLocationUseCase
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.videoprocessing.domain.CollectAllTextUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.DownloadedVideo
import org.neteinstein.instamaps.feature.videoprocessing.domain.FrameExtractorRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.TextRecognitionRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoDownloadRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoFrame
import org.neteinstein.instamaps.feature.videoprocessing.domain.VideoMetadataRepository
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
    var downloadCalled = false

    override suspend fun download(url: String): Result<DownloadedVideo> {
        downloadCalled = true
        return result
    }

    override suspend fun delete(video: DownloadedVideo) = Unit
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

private class FakeVideoMetadataRepository(
    private val result: Result<String> = Result.success(""),
) : VideoMetadataRepository {
    override suspend fun fetchDescription(url: String): Result<String> = result
}

private class FakeLocationRepository(
    private val result: Result<MapsDestination>,
) : LocationRepository {
    var lastTextReceived: String? = null

    override suspend fun resolveFromText(text: String): Result<MapsDestination> {
        lastTextReceived = text
        return result
    }
}

class ProcessSharedUrlUseCaseTest {
    private fun useCaseFor(
        frameText: String,
        locationResult: Result<MapsDestination> = Result.success(MapsDestination(query = "Central Park, New York, USA")),
        videoDownloadResult: Result<DownloadedVideo> = Result.success(DownloadedVideo(File("fake-video.mp4"))),
        descriptionResult: Result<String> = Result.success(""),
    ): Triple<ProcessSharedUrlUseCase, FakeLocationRepository, FakeVideoDownloadRepository> {
        val bitmap = mock<Bitmap>()
        val locationRepository = FakeLocationRepository(locationResult)
        val videoDownloadRepository = FakeVideoDownloadRepository(videoDownloadResult)
        val collectAllTextUseCase =
            CollectAllTextUseCase(
                videoMetadataRepository = FakeVideoMetadataRepository(descriptionResult),
                videoDownloadRepository = videoDownloadRepository,
                frameExtractorRepository = FakeFrameExtractorRepository(listOf(VideoFrame(0L, bitmap))),
                textRecognitionRepository = FakeTextRecognitionRepository(mapOf(bitmap to frameText)),
                dispatcherProvider = TestDispatcherProvider(),
            )
        val useCase =
            ProcessSharedUrlUseCase(
                collectAllTextUseCase = collectAllTextUseCase,
                resolveLocationUseCase = ResolveLocationUseCase(locationRepository),
            )
        return Triple(useCase, locationRepository, videoDownloadRepository)
    }

    @Test
    fun `resolves to Found when Gemini returns a place`() =
        runTest {
            val destination = MapsDestination(query = "Central Park, New York, USA")
            val (useCase, _, _) =
                useCaseFor(
                    frameText = "Central Park vibes",
                    locationResult = Result.success(destination),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park, New York, USA", found.displayName)
            assertEquals("Central Park, New York, USA", found.destination.query)
        }

    @Test
    fun `sends combined caption and frame OCR text to Gemini`() =
        runTest {
            val destination = MapsDestination(query = "Eiffel Tower, Paris, France")
            val (useCase, locationRepository, _) =
                useCaseFor(
                    frameText = "Tour Eiffel",
                    descriptionResult = Result.success("Paris trip"),
                    locationResult = Result.success(destination),
                )

            useCase("https://instagram.com/reel/abc").toList()

            val textSent = locationRepository.lastTextReceived.orEmpty()
            assertTrue("Caption should be in sent text", "Paris trip" in textSent)
            assertTrue("Frame OCR should be in sent text", "Tour Eiffel" in textSent)
        }

    @Test
    fun `emits CheckingDescription, Downloading, ExtractingFrames and Geocoding progress steps`() =
        runTest {
            val (useCase, _, _) = useCaseFor(frameText = "some text")

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.contains(ShareProcessingProgress.CheckingDescription))
            assertTrue(progress.contains(ShareProcessingProgress.Downloading))
            assertTrue(progress.contains(ShareProcessingProgress.ExtractingFrames))
            assertTrue(progress.contains(ShareProcessingProgress.Geocoding))
        }

    @Test
    fun `emits NotFound when Gemini cannot identify the location`() =
        runTest {
            val (useCase, _, _) =
                useCaseFor(
                    frameText = "just a nice video",
                    locationResult = Result.failure(AppError.NotFound("Gemini could not identify a specific place")),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.last() is ShareProcessingProgress.NotFound)
        }

    @Test
    fun `emits NotFound when no text at all is found in the video`() =
        runTest {
            val (useCase, _, _) = useCaseFor(frameText = "")

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.last() is ShareProcessingProgress.NotFound)
            assertFalse(progress.contains(ShareProcessingProgress.Geocoding))
        }

    @Test
    fun `propagates a download failure as a terminal Failed state`() =
        runTest {
            val error = AppError.Network("boom")
            val (useCase, _, _) = useCaseFor(frameText = "", videoDownloadResult = Result.failure(error))

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertEquals(
                listOf(
                    ShareProcessingProgress.CheckingDescription,
                    ShareProcessingProgress.Downloading,
                    ShareProcessingProgress.Failed(error),
                ),
                progress,
            )
        }
}
