package org.neteinstein.instamaps.feature.geocoding.domain

import org.neteinstein.instamaps.core.common.LatLng

/**
 * A single place returned by the Places SDK text search, trimmed down to what the rest of the
 * app needs: enough to build a Google Maps deep link ([placeId]) and to show the user what was
 * found ([name], [address]).
 */
data class GeocodedPlace(
    val placeId: String,
    val name: String,
    val address: String?,
    val latLng: LatLng,
)
