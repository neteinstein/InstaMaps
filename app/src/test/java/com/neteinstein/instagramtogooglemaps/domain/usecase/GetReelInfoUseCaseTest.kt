package com.neteinstein.instagramtogooglemaps.domain.usecase

import com.neteinstein.instagramtogooglemaps.domain.model.ReelInfo
import com.neteinstein.instagramtogooglemaps.domain.repository.LocationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetReelInfoUseCaseTest {

    private lateinit var repository: LocationRepository
    private lateinit var useCase: GetReelInfoUseCase

    @Before
    fun setUp() {
        repository = mock()
        useCase = GetReelInfoUseCase(repository)
    }

    @Test
    fun `invoke returns failure for non-Instagram URL`() = runTest {
        val result = useCase("https://www.google.com/some-page")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invoke returns failure for empty URL`() = runTest {
        val result = useCase("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke calls repository for valid Instagram URL`() = runTest {
        val url = "https://www.instagram.com/reel/ABC123"
        val expected = ReelInfo(url = url, description = "Test caption", authorName = "user123")
        whenever(repository.getReelInfo(url)).thenReturn(Result.success(expected))

        val result = useCase(url)
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `invoke calls repository for instagr am short URL`() = runTest {
        val url = "https://instagr.am/p/ABC123"
        val expected = ReelInfo(url = url, description = "Short URL test", authorName = "user")
        whenever(repository.getReelInfo(url)).thenReturn(Result.success(expected))

        val result = useCase(url)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `invoke propagates repository failure`() = runTest {
        val url = "https://www.instagram.com/reel/ABC123"
        val exception = RuntimeException("Network error")
        whenever(repository.getReelInfo(url)).thenReturn(Result.failure(exception))

        val result = useCase(url)
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
