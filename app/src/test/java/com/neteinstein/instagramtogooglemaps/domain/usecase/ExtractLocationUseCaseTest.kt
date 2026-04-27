package org.neteinstein.instamaps.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExtractLocationUseCaseTest {
    private lateinit var useCase: ExtractLocationUseCase

    @Before
    fun setUp() {
        useCase = ExtractLocationUseCase()
    }

    @Test
    fun `invoke returns null when text is blank`() {
        assertNull(useCase(""))
        assertNull(useCase("   "))
    }

    @Test
    fun `invoke extracts location from explicit location prefix`() {
        val text = "Amazing sunset! Location: Santa Monica Beach, California"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("Santa Monica Beach, California", result!!.name)
    }

    @Test
    fun `invoke extracts location from 'at' prefix`() {
        val text = "Chilling at: Central Park NYC"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("Central Park NYC", result!!.name)
    }

    @Test
    fun `invoke extracts location from pin emoji`() {
        val text = "Beautiful place!\n📍 Eiffel Tower, Paris"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("Eiffel Tower, Paris", result!!.name)
    }

    @Test
    fun `invoke extracts location from camelCase hashtag`() {
        val text = "Great trip! #NewYorkCity #travel #summer"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("New York City", result!!.name)
    }

    @Test
    fun `invoke extracts coordinates`() {
        val text = "Here are the coordinates: 48.8566, 2.3522"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("48.8566, 2.3522", result!!.name)
    }

    @Test
    fun `invoke returns null when no location found`() {
        val text = "Just a random post with no location info at all!"
        val result = useCase(text)
        assertNull(result)
    }

    @Test
    fun `invoke returns raw text in result`() {
        val text = "Location: Rome, Italy"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals(text, result!!.rawText)
    }

    @Test
    fun `invoke prefers explicit location over hashtag`() {
        val text = "Location: Vatican City #Rome #Italy"
        val result = useCase(text)
        assertNotNull(result)
        assertEquals("Vatican City", result!!.name.trim())
    }
}
