package org.neteinstein.instamaps.feature.maps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuildMapsDeepLinkUseCaseTest {
    private val useCase = BuildMapsDeepLinkUseCase()

    @Test
    fun `builds query-only link when placeId is absent`() {
        val destination = MapsDestination(query = "Dishoom London")

        val url = useCase(destination)

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Dishoom+London",
            url,
        )
    }

    @Test
    fun `appends query_place_id when placeId is present`() {
        val destination = MapsDestination(query = "Dishoom London", placeId = "ChIJd8BlQ2BZwokRAFUEcm_qrcA")

        val url = useCase(destination)

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Dishoom+London" +
                "&query_place_id=ChIJd8BlQ2BZwokRAFUEcm_qrcA",
            url,
        )
    }

    @Test
    fun `url-encodes special characters in the query`() {
        val destination = MapsDestination(query = "Café & Bar, Paris")

        val url = useCase(destination)

        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9+%26+Bar%2C+Paris",
            url,
        )
    }

    @Test
    fun `blank placeId is treated as absent`() {
        val destination = MapsDestination(query = "Somewhere", placeId = "  ")

        val url = useCase(destination)

        assertEquals("https://www.google.com/maps/search/?api=1&query=Somewhere", url)
    }

    @Test
    fun `rejects blank query`() {
        assertThrows(IllegalArgumentException::class.java) {
            MapsDestination(query = "   ")
        }
    }
}
