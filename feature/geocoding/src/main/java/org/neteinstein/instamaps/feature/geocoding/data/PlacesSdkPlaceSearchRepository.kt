package org.neteinstein.instamaps.feature.geocoding.data

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.kotlin.searchByTextRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.common.LatLng
import org.neteinstein.instamaps.core.common.describeOrDefault
import org.neteinstein.instamaps.core.common.safeCall
import org.neteinstein.instamaps.core.settings.domain.AppSettingsRepository
import org.neteinstein.instamaps.feature.geocoding.domain.GeocodedPlace
import org.neteinstein.instamaps.feature.geocoding.domain.PlaceSearchRepository

/**
 * [PlaceSearchRepository] backed by the Places SDK for Android ("New" client,
 * [PlacesClient.searchByText]). Unlike a Koin-startup-time client built from a snapshot key,
 * this repository re-reads the Places API key from Settings ([AppSettingsRepository]) before
 * every search and (re)initializes the SDK whenever the key changes, so saving a new key on the
 * Settings screen takes effect immediately without an app restart - the Places SDK explicitly
 * supports calling `initializeWithNewPlacesApiEnabled` again to rotate the key used by an
 * already-created [PlacesClient]. [mutex] guards the cached client/key pair since
 * `ProcessSharedUrlWorker` can run multiple shares concurrently.
 */
class PlacesSdkPlaceSearchRepository(
    private val context: Context,
    private val settingsRepository: AppSettingsRepository,
) : PlaceSearchRepository {
    private val mutex = Mutex()
    private var initializedApiKey: String? = null
    private var placesClient: PlacesClient? = null

    override suspend fun searchByText(query: String): Result<List<GeocodedPlace>> {
        val apiKey = settingsRepository.observePlacesApiKey().first()
        if (apiKey.isNullOrBlank()) {
            return Result.failure(
                AppError.MissingConfiguration("No Places API key configured - add one in Settings"),
            )
        }

        val client = clientFor(apiKey)
        return safeCall(mapError = { AppError.Network(it.describeOrDefault("Places search failed"), it) }) {
            val request =
                searchByTextRequest(
                    textQuery = query,
                    placeFields =
                        listOf(
                            Place.Field.ID,
                            Place.Field.DISPLAY_NAME,
                            Place.Field.FORMATTED_ADDRESS,
                            Place.Field.LOCATION,
                        ),
                )

            client.searchByText(request).await().places.mapNotNull { it.toGeocodedPlaceOrNull() }
        }
    }

    private suspend fun clientFor(apiKey: String): PlacesClient =
        mutex.withLock {
            if (initializedApiKey != apiKey) {
                Places.initializeWithNewPlacesApiEnabled(context, apiKey)
                placesClient = Places.createClient(context)
                initializedApiKey = apiKey
            }
            checkNotNull(placesClient)
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
