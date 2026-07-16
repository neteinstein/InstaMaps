package org.neteinstein.instamaps.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.android.inject
import org.neteinstein.instamaps.core.designsystem.theme.InstaMapsTheme
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.share.presentation.ShareRoute
import org.neteinstein.instamaps.feature.share.presentation.ShareScreen
import org.neteinstein.instamaps.feature.share.presentation.ShareUiState
import org.neteinstein.instamaps.feature.share.work.ShareDeepLink

/**
 * Single-Activity host for all three ways InstaMaps can be entered - see the three
 * `<intent-filter>` blocks on this activity in the manifest:
 *
 * 1. Plain launcher tap: no shared text, renders [ShareUiState.Idle] directly.
 * 2. Instagram/TikTok share ([Intent.ACTION_SEND], `text/plain`): renders [ShareRoute], which owns
 *    the whole download/OCR/geocode pipeline and its animated UI.
 * 3. Result-notification tap ([ShareDeepLink.ACTION_OPEN_MAPS_DESTINATION], fired once the
 *    background [org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker] finds a
 *    place): a pure trampoline - hand off to [MapsLauncher] and finish immediately, without ever
 *    drawing this app's own UI, since the pipeline already completed in the background.
 */
class MainActivity : ComponentActivity() {
    private val mapsLauncher: MapsLauncher by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (tryHandleMapsDeepLink(intent)) {
            finish()
            return
        }

        val sharedText = intent.extractSharedText()

        setContent {
            InstaMapsTheme {
                if (sharedText != null) {
                    ShareRoute(sharedText = sharedText)
                } else {
                    ShareScreen(
                        uiState = ShareUiState.Idle,
                        onOpenMaps = { mapsLauncher.launch(it) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun tryHandleMapsDeepLink(intent: Intent): Boolean {
        if (intent.action != ShareDeepLink.ACTION_OPEN_MAPS_DESTINATION) return false

        val query = intent.getStringExtra(ShareDeepLink.EXTRA_MAPS_QUERY)
        if (query.isNullOrBlank()) return false

        val destination = MapsDestination(query = query, placeId = intent.getStringExtra(ShareDeepLink.EXTRA_PLACE_ID))
        mapsLauncher.launch(destination)
        return true
    }

    private fun Intent.extractSharedText(): String? {
        if (action != Intent.ACTION_SEND || type != "text/plain") return null
        return getStringExtra(Intent.EXTRA_TEXT)
    }
}
