package org.neteinstein.instamaps.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.neteinstein.instamaps.domain.model.LocationInfo
import org.neteinstein.instamaps.domain.model.ReelInfo
import org.neteinstein.instamaps.domain.usecase.ExtractLocationUseCase
import org.neteinstein.instamaps.domain.usecase.GetReelInfoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getReelInfoUseCase: GetReelInfoUseCase
    private lateinit var extractLocationUseCase: ExtractLocationUseCase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getReelInfoUseCase = mock()
        extractLocationUseCase = mock()
        viewModel = MainViewModel(getReelInfoUseCase, extractLocationUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertTrue(viewModel.uiState.value is MainUiState.Idle)
    }

    @Test
    fun `processSharedUrl sets Loading state`() =
        runTest {
            whenever(getReelInfoUseCase(any())).thenReturn(
                Result.success(ReelInfo("url", "desc", "author")),
            )
            whenever(extractLocationUseCase(any())).thenReturn(
                LocationInfo("Test Location", "desc"),
            )

            viewModel.processSharedUrl("https://www.instagram.com/reel/test")
            assertEquals(MainUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `processSharedUrl sets LocationFound when location extracted`() =
        runTest {
            val url = "https://www.instagram.com/reel/test"
            val reelInfo = ReelInfo(url, "Location: Paris, France", "testuser")
            val locationInfo = LocationInfo("Paris, France", "Location: Paris, France")

            whenever(getReelInfoUseCase(url)).thenReturn(Result.success(reelInfo))
            whenever(extractLocationUseCase(reelInfo.description)).thenReturn(locationInfo)

            viewModel.processSharedUrl(url)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.LocationFound)
            assertEquals("Paris, France", (state as MainUiState.LocationFound).location)
        }

    @Test
    fun `processSharedUrl sets Error when no location found`() =
        runTest {
            val url = "https://www.instagram.com/reel/test"
            val reelInfo = ReelInfo(url, "Just a cool post", "testuser")

            whenever(getReelInfoUseCase(url)).thenReturn(Result.success(reelInfo))
            whenever(extractLocationUseCase(reelInfo.description)).thenReturn(null)

            viewModel.processSharedUrl(url)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Error)
        }

    @Test
    fun `processSharedUrl sets Error when fetch fails`() =
        runTest {
            val url = "https://www.instagram.com/reel/test"
            val exception = RuntimeException("Network error")

            whenever(getReelInfoUseCase(url)).thenReturn(Result.failure(exception))

            viewModel.processSharedUrl(url)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Error)
            assertEquals("Network error", (state as MainUiState.Error).message)
        }

    @Test
    fun `processSharedUrl sets Error with default message when exception has no message`() =
        runTest {
            val url = "https://www.instagram.com/reel/test"
            whenever(getReelInfoUseCase(url)).thenReturn(Result.failure(RuntimeException()))

            viewModel.processSharedUrl(url)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Error)
            assertEquals("Failed to fetch reel information", (state as MainUiState.Error).message)
        }
}
