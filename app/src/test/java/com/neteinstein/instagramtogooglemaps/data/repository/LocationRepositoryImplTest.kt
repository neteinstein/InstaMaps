package com.neteinstein.instagramtogooglemaps.data.repository

import com.neteinstein.instagramtogooglemaps.data.api.InstagramOEmbedApi
import com.neteinstein.instagramtogooglemaps.data.api.model.OEmbedResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LocationRepositoryImplTest {
    private lateinit var api: InstagramOEmbedApi
    private lateinit var repository: LocationRepositoryImpl

    @Before
    fun setUp() {
        api = mock()
        repository = LocationRepositoryImpl(api)
    }

    @Test
    fun `getReelInfo returns success with reel info`() =
        runTest {
            val url = "https://www.instagram.com/reel/ABC123"
            val response =
                OEmbedResponse(
                    title = "Beautiful sunset at Venice Beach 📍",
                    authorName = "traveler_jane",
                    html = "<blockquote></blockquote>",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                )
            whenever(api.getOEmbed(url)).thenReturn(response)

            val result = repository.getReelInfo(url)
            assertTrue(result.isSuccess)
            val reelInfo = result.getOrNull()!!
            assertEquals(url, reelInfo.url)
            assertEquals("Beautiful sunset at Venice Beach 📍", reelInfo.description)
            assertEquals("traveler_jane", reelInfo.authorName)
        }

    @Test
    fun `getReelInfo handles null title gracefully`() =
        runTest {
            val url = "https://www.instagram.com/reel/ABC123"
            val response =
                OEmbedResponse(
                    title = null,
                    authorName = "user",
                    html = null,
                    thumbnailUrl = null,
                )
            whenever(api.getOEmbed(url)).thenReturn(response)

            val result = repository.getReelInfo(url)
            assertTrue(result.isSuccess)
            assertEquals("", result.getOrNull()!!.description)
        }

    @Test
    fun `getReelInfo returns failure on network exception`() =
        runTest {
            val url = "https://www.instagram.com/reel/ABC123"
            whenever(api.getOEmbed(url)).thenThrow(RuntimeException("Timeout"))

            val result = repository.getReelInfo(url)
            assertTrue(result.isFailure)
            assertEquals("Timeout", result.exceptionOrNull()!!.message)
        }
}
