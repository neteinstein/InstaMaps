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
import org.neteinstein.instamaps.feature.history.presentation.HistoryRoute
import org.neteinstein.instamaps.feature.instagramauth.presentation.InstagramLoginRoute
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.permissions.presentation.PermissionsScreen
import org.neteinstein.instamaps.feature.permissions.presentation.rememberAppReadiness
import org.neteinstein.instamaps.feature.settings.presentation.SettingsRoute
import org.neteinstein.instamaps.feature.share.presentation.ShareRoute
import org.neteinstein.instamaps.feature.share.work.ShareDeepLink

/**
 * Single-Activity host for all three ways InstaMaps can be entered - see the three
 * `<intent-filter>` blocks on this activity in the manifest:
 *
 * 1. Plain launcher tap: no shared text, renders [ShareRoute] with a `null` `sharedText` once
 *    ready (see below) - otherwise [PermissionsScreen] shows instead.
 * 2. Instagram/TikTok share ([Intent.ACTION_SEND], `text/plain`): renders [ShareRoute] with the
 *    shared text once ready - it owns the whole download/OCR/geocode pipeline and its animated
 *    UI. [sharedText] is hoisted to a Compose state so a second share arriving via [onNewIntent] -
 *    the default `launchMode`/task-affinity combo routes a repeat `ACTION_SEND` into this same
 *    activity instance rather than a fresh one - re-triggers the pipeline instead of being
 *    silently dropped.
 * 3. Result-notification tap ([ShareDeepLink.ACTION_OPEN_MAPS_DESTINATION], fired once the
 *    background [org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker] finds a
 *    place): a pure trampoline - hand off to [MapsLauncher] and finish immediately, without ever
 *    drawing this app's own UI, since the pipeline already completed in the background.
 *
 * The [Screen.SHARE] branch is gated on [rememberAppReadiness]: a missing Gemini API key or a
 * missing runtime permission (currently just notifications - see
 * `org.neteinstein.instamaps.core.permissions.requiredRuntimePermissions`) renders [PermissionsScreen]
 * - which explains what InstaMaps does and why each requirement is needed - instead of
 * [ShareRoute]. Readiness is recomputed on every recomposition (including on resume), so this
 * isn't just a first-run check: revoking a permission from system Settings and reopening
 * InstaMaps, or coming back from the system App Settings page, lands back on [PermissionsScreen]
 * again automatically, and the moment everything's resolved the same recomposition flips over to
 * [ShareRoute] without any explicit "continue" step.
 *
 * Also owns the Share/Settings/History/Instagram-login switch: there's no Navigation-Compose in
 * this app, so opening Settings or History (the top-right buttons on the main screen) or the
 * Instagram login screen
 * (from a warning banner or [org.neteinstein.instamaps.feature.share.presentation.ShareUiState.AuthRequired])
 * is just a local [Screen] flag flipped back by that screen's own back arrow, a successful login,
 * or the system back gesture/button. Settings/History/Instagram-login stay reachable regardless of
 * readiness - [PermissionsScreen]'s "Add API key" CTA needs to reach Settings even before the app
 * is ready.
 *
 * Also bridges [org.neteinstein.instamaps.feature.settings.presentation.SettingsRoute]'s "Test a
 * link" field into the same pipeline a real OS share uses: `feature:settings` has no dependency on
 * `feature:share` (feature modules never depend on each other directly), so [SettingsRoute]'s
 * `onTestLink` callback is supplied here, feeding the submitted URL through [submitSharedText] -
 * the exact same path [onNewIntent] uses for a repeat share - and flipping [Screen] back to
 * [Screen.SHARE] so the result is visible immediately.
 */
class MainActivity : ComponentActivity() {
    private val mapsLauncher: MapsLauncher by inject()
    private var sharedText by mutableStateOf<String?>(null)

    // Bumped every time sharedText is (re)submitted, real share or "Test a link" alike. Compose's
    // LaunchedEffect(sharedText) in ShareRoute wouldn't restart on two structurally-equal URLs in
    // a row - see ShareRoute's sharedTextRequestId param - so this nonce is what actually forces a
    // retrigger.
    private var sharedTextRequestId by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (tryHandleMapsDeepLink(intent)) {
            finish()
            return
        }

        submitSharedText(intent.extractSharedText())

        setContent {
            InstaMapsTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.SHARE) }
                BackHandler(enabled = screen != Screen.SHARE) { screen = Screen.SHARE }

                when (screen) {
                    Screen.SETTINGS ->
                        SettingsRoute(
                            onBack = { screen = Screen.SHARE },
                            onTestLink = { url ->
                                submitSharedText(url)
                                screen = Screen.SHARE
                            },
                        )
                    Screen.HISTORY -> HistoryRoute(onBack = { screen = Screen.SHARE })
                    Screen.INSTAGRAM_LOGIN ->
                        InstagramLoginRoute(
                            onBack = { screen = Screen.SHARE },
                            onLoginSuccess = { screen = Screen.SHARE },
                        )
                    Screen.SHARE -> {
                        val readiness = rememberAppReadiness()
                        if (readiness.isReady) {
                            ShareRoute(
                                sharedText = sharedText,
                                sharedTextRequestId = sharedTextRequestId,
                                onOpenSettings = { screen = Screen.SETTINGS },
                                onOpenHistory = { screen = Screen.HISTORY },
                                onNeedsInstagramLogin = { screen = Screen.INSTAGRAM_LOGIN },
                            )
                        } else {
                            PermissionsScreen(
                                readiness = readiness,
                                onOpenSettings = { screen = Screen.SETTINGS },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (tryHandleMapsDeepLink(intent)) {
            finish()
            return
        }

        submitSharedText(intent.extractSharedText())
    }

    private fun submitSharedText(text: String?) {
        sharedText = text
        sharedTextRequestId++
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

    private enum class Screen { SHARE, SETTINGS, HISTORY, INSTAGRAM_LOGIN }
}
