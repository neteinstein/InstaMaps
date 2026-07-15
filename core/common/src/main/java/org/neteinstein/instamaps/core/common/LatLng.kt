package org.neteinstein.instamaps.core.common

/**
 * Framework-free coordinate pair shared across features (geocoding results, OCR/caption-parsed
 * coordinate candidates, map deep links) so no feature module needs to depend on another just to
 * pass a lat/lng around.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "latitude must be in [-90, 90], was $latitude" }
        require(longitude in -180.0..180.0) { "longitude must be in [-180, 180], was $longitude" }
    }
}
