package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.neteinstein.instamaps.core.common.LatLng

class LocationTextParserTest {
    private val parser = LocationTextParser()

    @Test
    fun `returns empty list when text is blank`() {
        assertEquals(emptyList<LocationCandidate>(), parser.parse(""))
        assertEquals(emptyList<LocationCandidate>(), parser.parse("   "))
    }

    @Test
    fun `extracts location from explicit location prefix`() {
        val candidates = parser.parse("Amazing sunset! Location: Santa Monica Beach, California")

        val match = candidates.filterIsInstance<LocationCandidate.PlaceName>().first()
        assertEquals("Santa Monica Beach, California", match.text)
    }

    @Test
    fun `extracts location from 'at' prefix`() {
        val candidates = parser.parse("Chilling at: Central Park NYC")

        val match = candidates.filterIsInstance<LocationCandidate.PlaceName>().first()
        assertEquals("Central Park NYC", match.text)
    }

    @Test
    fun `extracts location from pin emoji`() {
        val candidates = parser.parse("Beautiful place!\n📍 Eiffel Tower, Paris")

        val match = candidates.filterIsInstance<LocationCandidate.PlaceName>().first()
        assertEquals("Eiffel Tower, Paris", match.text)
    }

    @Test
    fun `extracts location from camelCase hashtag`() {
        val candidates = parser.parse("Great trip! #NewYorkCity #travel #summer")

        val match = candidates.filterIsInstance<LocationCandidate.PlaceName>().first()
        assertEquals("New York City", match.text)
    }

    @Test
    fun `extracts coordinates as a Coordinates candidate`() {
        val candidates = parser.parse("Here are the coordinates: 48.8566, 2.3522")

        val match = candidates.filterIsInstance<LocationCandidate.Coordinates>().first()
        assertEquals(LatLng(48.8566, 2.3522), match.latLng)
    }

    @Test
    fun `returns empty list when no location found`() {
        val candidates = parser.parse("Just a random post with no location info at all!")

        assertEquals(emptyList<LocationCandidate>(), candidates)
    }

    @Test
    fun `ranks explicit location above hashtag when both are present`() {
        val candidates = parser.parse("Location: Vatican City #Rome #Italy")

        val best = candidates.maxByOrNull { it.confidence } as LocationCandidate.PlaceName
        assertEquals("Vatican City", best.text)
    }

    @Test
    fun `returns both explicit and coordinate candidates when text has both signals`() {
        val candidates = parser.parse("Location: Somewhere nice\n48.8566, 2.3522")

        assertTrue(candidates.any { it is LocationCandidate.PlaceName })
        assertTrue(candidates.any { it is LocationCandidate.Coordinates })
    }

    @Test
    fun `ignores an out-of-range longitude that only looks like a coordinate pair`() {
        val candidates = parser.parse("Ratio 45.0, 200.5")

        assertTrue(candidates.filterIsInstance<LocationCandidate.Coordinates>().isEmpty())
    }
}
