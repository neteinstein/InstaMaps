package org.neteinstein.instamaps.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.koin.android.ext.android.inject
import org.neteinstein.instamaps.core.designsystem.theme.InstaMapsTheme
import org.neteinstein.instamaps.feature.instagramauth.presentation.InstagramLoginRoute
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.settings.presentation.SettingsRoute
import org.neteinstein.instamaps.feature.share.presentation.ShareRoute
import org.neteinstein.instamaps.feature.share.work.ShareDeepLink

/**
 * Single-Activity host for all three ways InstaMaps can be entered - see the three
 * `<intent-filter>` blocks on this activity in the manifest:
 *
 * 1. Plain launcher tap: no shared text, renders [ShareRoute] with a `null` `sharedText` - which
 *    is also what renders the main-screen readiness warnings (missing API key/permissions, no
 *    Instagram session) since they're computed inside [ShareRoute] regardless of how the screen
 *    was reached.
 * 2. Instagram/TikTok share ([Intent.ACTION_SEND], `text/plain`): renders [ShareRoute] with the
 *    shared text - it owns the whole download/OCR/geocode pipeline and its animated UI, but only
 *    starts it once ready; otherwise the main screen with warnings shows instead (see
 *    [ShareRoute]'s doc).
 * 3. Result-notification tap ([ShareDeepLink.ACTION_OPEN_MAPS_DESTINATION], fired once the
 *    background [org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker] finds a
 *    place): a pure trampoline - hand off to [MapsLauncher] and finish immediately, without ever
 *    drawing this app's own UI, since the pipeline already completed in the background.
 *
 * Also owns the Share/Settings/Instagram-login switch: there's no Navigation-Compose in this app,
 * so opening Settings (the top-right button on the main screen) or the Instagram login screen
 * (from a warning banner or [org.neteinstein.instamaps.feature.share.presentation.ShareUiState.AuthRequired])
 * is just a local [Screen] flag flipped back by that screen's own back arrow, a successful login,
 * or the system back gesture/button.
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
                var screen by rememberSaveable { mutableStateOf(Screen.SHARE) }
                BackHandler(enabled = screen != Screen.SHARE) { screen = Screen.SHARE }

                when (screen) {
                    Screen.SETTINGS -> SettingsRoute(onBack = { screen = Screen.SHARE })
                    Screen.INSTAGRAM_LOGIN ->
                        InstagramLoginRoute(
                            onBack = { screen = Screen.SHARE },
                            onLoginSuccess = { screen = Screen.SHARE },
                        )
                    Screen.SHARE ->
                        ShareRoute(
                            sharedText = sharedText,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onNeedsInstagramLogin = { screen = Screen.INSTAGRAM_LOGIN },
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

    private enum class Screen { SHARE, SETTINGS, INSTAGRAM_LOGIN }
}
