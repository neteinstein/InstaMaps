package org.neteinstein.instamaps.feature.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.neteinstein.instamaps.feature.maps.domain.BuildMapsDeepLinkUseCase
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

/**
 * Fires the Intent that opens a [MapsDestination]. Tries the native Google Maps app first;
 * if it isn't installed, falls back to whatever app/browser can handle the same universal link.
 *
 * Always adds `FLAG_ACTIVITY_NEW_TASK`: this class is Koin-wired with the application [Context]
 * (see `mapsModule`), and callers may also invoke it from non-Activity contexts (a background
 * notification tap trampoline, a `CoroutineWorker`), both of which throw at runtime without this
 * flag - it is harmless to always set it, since Maps/the browser fallback always opens in its own
 * task regardless of caller context.
 */
class MapsLauncher(
    private val context: Context,
    private val buildMapsDeepLinkUseCase: BuildMapsDeepLinkUseCase,
) {
    fun launch(destination: MapsDestination): Boolean {
        val uri = Uri.parse(buildMapsDeepLinkUseCase(destination))

        val mapsAppIntent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(GOOGLE_MAPS_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        if (mapsAppIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapsAppIntent)
            return true
        }

        val fallbackIntent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        if (fallbackIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(fallbackIntent)
            return true
        }

        return false
    }
}
