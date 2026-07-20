package org.neteinstein.instamaps.feature.maps.domain

/**
 * Everything needed to build a Google Maps deep link for a resolved location.
 *
 * [query] is the human-readable text Maps falls back to if [placeId] can't be resolved
 * (e.g. the place name, or a "lat,lng" string) and must never be blank. [placeId] is the
 * precise place identifier when available; when null, Maps performs a text search using
 * [query] alone.
 */
data class MapsDestination(
    val query: String,
    val placeId: String? = null,
) {
    init {
        require(query.isNotBlank()) { "MapsDestination.query must not be blank" }
    }
}
