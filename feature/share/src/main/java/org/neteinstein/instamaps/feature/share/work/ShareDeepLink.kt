package org.neteinstein.instamaps.feature.share.work

/**
 * Contract for the result notification's tap action. `feature:share` builds an implicit
 * [android.content.Intent] scoped to the app's own package with this action, rather than
 * referencing `:app`'s launcher activity directly - `:app` depends on `feature:share`, not the
 * other way around. `:app` declares a matching `<intent-filter>` on its launcher activity and
 * reads these extras back out to call `MapsLauncher` once it's actually resumed.
 */
object ShareDeepLink {
    const val ACTION_OPEN_MAPS_DESTINATION = "org.neteinstein.instamaps.action.OPEN_MAPS_DESTINATION"
    const val EXTRA_MAPS_QUERY = "org.neteinstein.instamaps.extra.MAPS_QUERY"
    const val EXTRA_PLACE_ID = "org.neteinstein.instamaps.extra.PLACE_ID"
}
