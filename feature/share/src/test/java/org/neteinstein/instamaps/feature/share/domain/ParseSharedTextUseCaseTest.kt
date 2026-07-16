package org.neteinstein.instamaps.feature.share.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseSharedTextUseCaseTest {
    private val useCase = ParseSharedTextUseCase()

    @Test
    fun `extracts an Instagram reel URL and tags its platform`() {
        val result = useCase("Check this out! https://www.instagram.com/reel/Cabc123/?igsh=xyz")

        assertEquals(
            ParsedSharedUrl("https://www.instagram.com/reel/Cabc123/?igsh=xyz", SharedPlatform.INSTAGRAM),
            result,
        )
    }

    @Test
    fun `extracts a TikTok URL and tags its platform`() {
        val result = useCase("https://www.tiktok.com/@someone/video/123456")

        assertEquals(SharedPlatform.TIKTOK, result?.platform)
    }

    @Test
    fun `strips trailing sentence punctuation swept up by the greedy match`() {
        val result = useCase("Found this place: https://instagr.am/reel/abc123.")

        assertEquals("https://instagr.am/reel/abc123", result?.url)
    }

    @Test
    fun `tags an unrecognized host as UNKNOWN rather than failing`() {
        val result = useCase("https://example.com/video/1")

        assertEquals(SharedPlatform.UNKNOWN, result?.platform)
    }

    @Test
    fun `returns null when the text has no URL at all`() {
        val result = useCase("just some caption text with no link")

        assertNull(result)
    }
}
