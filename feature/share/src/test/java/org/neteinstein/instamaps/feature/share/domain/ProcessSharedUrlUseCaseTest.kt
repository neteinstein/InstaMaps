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
import org.neteinstein.instamaps.core.common.LatLng
import org.neteinstein.instamaps.feature.geocoding.domain.GeocodedPlace
import org.neteinstein.instamaps.feature.geocoding.domain.PlaceSearchRepository
import org.neteinstein.instamaps.feature.geocoding.domain.SearchPlaceUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.DownloadedVideo
import org.neteinstein.instamaps.feature.videoprocessing.domain.EntityExtractionRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesFromDescriptionUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.ExtractLocationCandidatesUseCase
import org.neteinstein.instamaps.feature.videoprocessing.domain.FrameExtractorRepository
import org.neteinstein.instamaps.feature.videoprocessing.domain.LocationTextAnalyzer
import org.neteinstein.instamaps.feature.videoprocessing.domain.LocationTextParser
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

private class FakeEntityExtractionRepository : EntityExtractionRepository {
    override suspend fun extractAddresses(text: String): Result<List<String>> = Result.success(emptyList())
}

private class FakeVideoMetadataRepository(
    private val result: Result<String> = Result.success(""),
) : VideoMetadataRepository {
    override suspend fun fetchDescription(url: String): Result<String> = result
}

private class FakePlaceSearchRepository(
    private val resultsByQuery: Map<String, Result<List<GeocodedPlace>>>,
) : PlaceSearchRepository {
    val receivedQueries = mutableListOf<String>()

    override suspend fun searchByText(query: String): Result<List<GeocodedPlace>> {
        receivedQueries += query
        return resultsByQuery[query] ?: Result.success(emptyList())
    }
}

class ProcessSharedUrlUseCaseTest {
    private fun useCaseFor(
        frameText: String,
        placeResultsByQuery: Map<String, Result<List<GeocodedPlace>>> = emptyMap(),
        videoDownloadResult: Result<DownloadedVideo> = Result.success(DownloadedVideo(File("fake-video.mp4"))),
        descriptionResult: Result<String> = Result.success(""),
    ): Triple<ProcessSharedUrlUseCase, FakePlaceSearchRepository, FakeVideoDownloadRepository> {
        val bitmap = mock<Bitmap>()
        val placeSearchRepository = FakePlaceSearchRepository(placeResultsByQuery)
        val videoDownloadRepository = FakeVideoDownloadRepository(videoDownloadResult)
        val locationTextAnalyzer = LocationTextAnalyzer(FakeEntityExtractionRepository(), LocationTextParser())
        val extractLocationCandidatesFromDescriptionUseCase =
            ExtractLocationCandidatesFromDescriptionUseCase(
                videoMetadataRepository = FakeVideoMetadataRepository(descriptionResult),
                locationTextAnalyzer = locationTextAnalyzer,
            )
        val extractLocationCandidatesUseCase =
            ExtractLocationCandidatesUseCase(
                videoDownloadRepository = videoDownloadRepository,
                frameExtractorRepository = FakeFrameExtractorRepository(listOf(VideoFrame(0L, bitmap))),
                textRecognitionRepository = FakeTextRecognitionRepository(mapOf(bitmap to frameText)),
                locationTextAnalyzer = locationTextAnalyzer,
                dispatcherProvider = TestDispatcherProvider(),
            )
        val useCase =
            ProcessSharedUrlUseCase(
                extractLocationCandidatesFromDescriptionUseCase = extractLocationCandidatesFromDescriptionUseCase,
                extractLocationCandidatesUseCase = extractLocationCandidatesUseCase,
                searchPlaceUseCase = SearchPlaceUseCase(placeSearchRepository),
            )
        return Triple(useCase, placeSearchRepository, videoDownloadRepository)
    }

    private val samplePlace =
        GeocodedPlace(
            placeId = "place-1",
            name = "Central Park",
            address = "New York, NY",
            latLng = LatLng(40.7829, -73.9654),
        )

    @Test
    fun `resolves the top-ranked place-name candidate to a Found destination`() =
        runTest {
            val (useCase, _, _) =
                useCaseFor(
                    frameText = "📍Central Park",
                    placeResultsByQuery = mapOf("Central Park" to Result.success(listOf(samplePlace))),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park", found.displayName)
            assertEquals("place-1", found.destination.placeId)
        }

    @Test
    fun `resolves a coordinates candidate directly without calling the search repository`() =
        runTest {
            val (useCase, placeSearchRepository, _) = useCaseFor(frameText = "Meet me at 48.8566, 2.3522")

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("48.8566,2.3522", found.destination.query)
            assertEquals(null, found.destination.placeId)
            assertTrue(placeSearchRepository.receivedQueries.isEmpty())
        }

    @Test
    fun `falls back to the next-ranked candidate when the top one does not resolve`() =
        runTest {
            // "📍Warehouse Cafe #CentralPark" yields two candidates: an explicit-marker
            // PlaceName("Warehouse Cafe", 0.9) ranked above a hashtag PlaceName("Central Park", 0.5).
            val (useCase, placeSearchRepository, _) =
                useCaseFor(
                    frameText = "📍Warehouse Cafe #CentralPark",
                    placeResultsByQuery =
                        mapOf(
                            "Warehouse Cafe" to Result.failure(AppError.NotFound("no such place")),
                            "Central Park" to Result.success(listOf(samplePlace)),
                        ),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park", found.displayName)
            assertEquals(listOf("Warehouse Cafe", "Central Park"), placeSearchRepository.receivedQueries)
        }

    @Test
    fun `emits NotFound without geocoding when no candidate was detected at all`() =
        runTest {
            val (useCase, placeSearchRepository, _) = useCaseFor(frameText = "just a nice video, no location here")

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.last() is ShareProcessingProgress.NotFound)
            assertFalse(progress.contains(ShareProcessingProgress.Geocoding))
            assertTrue(placeSearchRepository.receivedQueries.isEmpty())
        }

    @Test
    fun `emits NotFound when every detected candidate fails to resolve`() =
        runTest {
            val (useCase, _, _) =
                useCaseFor(
                    frameText = "📍Warehouse Cafe #CentralPark",
                    placeResultsByQuery =
                        mapOf(
                            "Warehouse Cafe" to Result.failure(AppError.NotFound("no such place")),
                            "Central Park" to Result.failure(AppError.NotFound("no such place")),
                        ),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.last() is ShareProcessingProgress.NotFound)
        }

    @Test
    fun `caps resolution attempts instead of querying every low-confidence fallback candidate`() =
        runTest {
            // Nine distinct two-word runs, each separated by a lowercase "and" so
            // LocationTextParser's capitalized-phrase fallback (see LocationTextParserTest)
            // surfaces nine separate low-confidence PlaceName candidates from one frame - more
            // than ProcessSharedUrlUseCase should ever query the Places SDK with for a single
            // share.
            val frameText =
                "Alpha Bravo and Charlie Delta and Echo Foxtrot and Golf Hotel and India Juliet " +
                    "and Kilo Lima and Mike November and Oscar Papa and Quebec Romeo"
            val (useCase, placeSearchRepository, _) = useCaseFor(frameText = frameText)

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertTrue(progress.last() is ShareProcessingProgress.NotFound)
            assertEquals(8, placeSearchRepository.receivedQueries.size)
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

    @Test
    fun `maps Downloading and ExtractingFrames progress through unchanged`() =
        runTest {
            val (useCase, _, _) = useCaseFor(frameText = "no location signal")

            val progress = useCase("https://instagram.com/reel/abc").toList()

            assertEquals(ShareProcessingProgress.CheckingDescription, progress.first())
            assertTrue(progress.contains(ShareProcessingProgress.Downloading))
            assertTrue(progress.contains(ShareProcessingProgress.ExtractingFrames))
        }

    @Test
    fun `resolves directly from the description without downloading the video`() =
        runTest {
            val (useCase, placeSearchRepository, videoDownloadRepository) =
                useCaseFor(
                    frameText = "no location signal",
                    descriptionResult = Result.success("📍Central Park"),
                    placeResultsByQuery = mapOf("Central Park" to Result.success(listOf(samplePlace))),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park", found.displayName)
            assertEquals(listOf("Central Park"), placeSearchRepository.receivedQueries)
            assertFalse(videoDownloadRepository.downloadCalled)
            assertEquals(ShareProcessingProgress.CheckingDescription, progress.first())
        }

    @Test
    fun `falls back to the video pipeline when the description candidate does not resolve`() =
        runTest {
            val (useCase, placeSearchRepository, videoDownloadRepository) =
                useCaseFor(
                    frameText = "📍Central Park",
                    descriptionResult = Result.success("📍Warehouse Cafe"),
                    placeResultsByQuery =
                        mapOf(
                            "Warehouse Cafe" to Result.failure(AppError.NotFound("no such place")),
                            "Central Park" to Result.success(listOf(samplePlace)),
                        ),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park", found.displayName)
            assertEquals(listOf("Warehouse Cafe", "Central Park"), placeSearchRepository.receivedQueries)
            assertTrue(videoDownloadRepository.downloadCalled)
        }

    @Test
    fun `falls back to the video pipeline when the description has no location signal`() =
        runTest {
            val (useCase, _, videoDownloadRepository) =
                useCaseFor(
                    frameText = "📍Central Park",
                    descriptionResult = Result.success("just a fun day out, no location mentioned"),
                    placeResultsByQuery = mapOf("Central Park" to Result.success(listOf(samplePlace))),
                )

            val progress = useCase("https://instagram.com/reel/abc").toList()

            val found = progress.filterIsInstance<ShareProcessingProgress.Found>().single()
            assertEquals("Central Park", found.displayName)
            assertTrue(videoDownloadRepository.downloadCalled)
        }
}
