package org.neteinstein.instamaps.feature.share.work

import org.json.JSONArray
import org.json.JSONObject
import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

/**
 * `androidx.work.Data` only supports primitives (strings, primitive arrays, ...) - there's no way
 * to attach a `List<ResolvedLocation>` to a `WorkInfo` directly. [ProcessSharedUrlWorker]
 * serializes the ranked list Gemini returned to this JSON string under
 * [ProcessSharedUrlWorker.KEY_LOCATIONS_JSON] on success; `ShareViewModel` parses it back out with
 * [toResolvedLocations] once it observes that `WorkInfo`. Kept `internal` (not `private`) so it's
 * shared between those two call sites - both inside `feature:share` - without becoming part of
 * any other module's contract.
 */
internal fun List<ResolvedLocation>.toJson(): String {
    val array = JSONArray()
    forEach { location ->
        array.put(
            JSONObject().apply {
                put("name", location.name)
                put("address", location.address ?: JSONObject.NULL)
                put("query", location.destination.query)
                put("placeId", location.destination.placeId ?: JSONObject.NULL)
            },
        )
    }
    return array.toString()
}

internal fun String.toResolvedLocations(): List<ResolvedLocation> {
    val array = JSONArray(this)
    return (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ResolvedLocation(
            name = item.getString("name"),
            address = item.optStringOrNull("address"),
            destination =
                MapsDestination(
                    query = item.getString("query"),
                    placeId = item.optStringOrNull("placeId"),
                ),
        )
    }
}

private fun JSONObject.optStringOrNull(name: String): String? = if (isNull(name)) null else optString(name)
