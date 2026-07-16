package org.neteinstein.instamaps.feature.geocoding.data

import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.kotlin.searchByTextRequest
import kotlinx.coroutines.tasks.await
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.LatLng
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.feature.geocoding.domain.GeocodedPlace
import org.neteinstein.instamaps.feature.geocoding.domain.PlaceSearchRepository

/**
 * [PlaceSearchRepository] backed by the Places SDK for Android ("New" client,
 * [PlacesClient.searchByText]). Network/SDK exceptions are translated to [AppError] via
 * [safeCall] so the domain layer never sees Places-SDK-specific exception types.
 */
class PlacesSdkPlaceSearchRepository(
    private val placesClient: PlacesClient,
) : PlaceSearchRepository {
    override suspend fun searchByText(query: String): Result<List<GeocodedPlace>> =
        safeCall(mapError = { AppError.Network("Places search failed", it) }) {
            val request =
                searchByTextRequest(
                    textQuery = query,
                    placeFields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.FORMATTED_ADDRESS, Place.Field.LOCATION),
                )

            placesClient.searchByText(request).await().places.mapNotNull { it.toGeocodedPlaceOrNull() }
        }

    private fun Place.toGeocodedPlaceOrNull(): GeocodedPlace? {
        val placeId = id ?: return null
        val name = displayName ?: return null
        val location = location ?: return null
        return GeocodedPlace(
            placeId = placeId,
            name = name,
            address = formattedAddress,
            latLng = LatLng(latitude = location.latitude, longitude = location.longitude),
        )
    }
}
