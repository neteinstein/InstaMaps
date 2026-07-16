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
            }
        if (mapsAppIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapsAppIntent)
            return true
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, uri)
        if (fallbackIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(fallbackIntent)
            return true
        }

        return false
    }
}
