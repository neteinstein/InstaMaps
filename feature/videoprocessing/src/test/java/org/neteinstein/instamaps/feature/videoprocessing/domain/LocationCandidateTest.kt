package org.neteinstein.instamaps.feature.videoprocessing.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.neteinstein.instamaps.core.common.LatLng

class LocationCandidateTest {
    @Test
    fun `drops an exact-duplicate lower-confidence PlaceName`() {
        val candidates =
            listOf(
                LocationCandidate.PlaceName(text = "Dishoom Shoreditch", confidence = 0.9f),
                LocationCandidate.PlaceName(text = "Dishoom Shoreditch", confidence = 0.4f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(listOf(LocationCandidate.PlaceName("Dishoom Shoreditch", 0.9f)), result)
    }

    @Test
    fun `drops a duplicate PlaceName that only differs by case or surrounding whitespace`() {
        val candidates =
            listOf(
                LocationCandidate.PlaceName(text = "Dishoom Shoreditch", confidence = 0.9f),
                LocationCandidate.PlaceName(text = " DISHOOM SHOREDITCH ", confidence = 0.4f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(1, result.size)
    }

    @Test
    fun `drops a lower-confidence PlaceName whose text is a substring of a kept candidate`() {
        val candidates =
            listOf(
                LocationCandidate.PlaceName(text = "Eiffel Tower, Paris", confidence = 0.9f),
                LocationCandidate.PlaceName(text = "Eiffel Tower", confidence = 0.4f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(listOf(LocationCandidate.PlaceName("Eiffel Tower, Paris", 0.9f)), result)
    }

    @Test
    fun `keeps distinct PlaceName candidates that do not overlap`() {
        val candidates =
            listOf(
                LocationCandidate.PlaceName(text = "Tony's Pizza", confidence = 0.4f),
                LocationCandidate.PlaceName(text = "Central Park", confidence = 0.4f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(candidates, result)
    }

    @Test
    fun `drops a duplicate Coordinates candidate with the same lat lng`() {
        val candidates =
            listOf(
                LocationCandidate.Coordinates(LatLng(48.8566, 2.3522), confidence = 0.95f),
                LocationCandidate.Coordinates(LatLng(48.8566, 2.3522), confidence = 0.95f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(1, result.size)
    }

    @Test
    fun `keeps distinct Coordinates candidates`() {
        val candidates =
            listOf(
                LocationCandidate.Coordinates(LatLng(48.8566, 2.3522), confidence = 0.95f),
                LocationCandidate.Coordinates(LatLng(40.7829, -73.9654), confidence = 0.95f),
            )

        val result = candidates.mostConfidentDistinct()

        assertEquals(candidates, result)
    }

    @Test
    fun `returns an empty list for an empty input`() {
        assertEquals(emptyList<LocationCandidate>(), emptyList<LocationCandidate>().mostConfidentDistinct())
    }
}
