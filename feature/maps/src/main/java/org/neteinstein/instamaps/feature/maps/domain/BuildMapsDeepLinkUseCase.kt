package org.neteinstein.instamaps.feature.maps.domain

import java.net.URLEncoder

/**
 * Builds a Google Maps "search" universal link
 * (https://developers.google.com/maps/documentation/urls/get-started#search-action) from a
 * [MapsDestination]. Kept as a plain string builder (no `android.net.Uri`) so it is exercisable
 * by pure JUnit tests without Robolectric/instrumentation.
 */
class BuildMapsDeepLinkUseCase {
    operator fun invoke(destination: MapsDestination): String {
        val encodedQuery = URLEncoder.encode(destination.query, "UTF-8")
        val base = "https://www.google.com/maps/search/?api=1&query=$encodedQuery"
        val placeId = destination.placeId
        return if (placeId.isNullOrBlank()) {
            base
        } else {
            "$base&query_place_id=${URLEncoder.encode(placeId, "UTF-8")}"
        }
    }
}
