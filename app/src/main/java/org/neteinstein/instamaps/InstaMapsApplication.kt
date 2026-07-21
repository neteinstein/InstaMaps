package org.neteinstein.instamaps

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.neteinstein.instamaps.core.common.di.commonModule
import org.neteinstein.instamaps.core.history.di.historyModule
import org.neteinstein.instamaps.core.instagramauth.di.instagramAuthModule
import org.neteinstein.instamaps.core.settings.di.settingsModule
import org.neteinstein.instamaps.feature.geocoding.di.geocodingModule
import org.neteinstein.instamaps.feature.history.di.historyUiModule
import org.neteinstein.instamaps.feature.instagramauth.di.instagramAuthUiModule
import org.neteinstein.instamaps.feature.maps.di.mapsModule
import org.neteinstein.instamaps.feature.permissions.di.permissionsUiModule
import org.neteinstein.instamaps.feature.settings.di.settingsUiModule
import org.neteinstein.instamaps.feature.share.di.shareModule
import org.neteinstein.instamaps.feature.videoprocessing.di.videoProcessingModule

/**
 * Composition root: starts Koin once, wiring every feature/core module's DI graph together. The
 * Gemini API key is entered at runtime from
 * `core:settings`'s DataStore-backed repository, populated by the user on the Settings screen
 * (`feature:settings`), so [settingsModule] must be present before [geocodingModule]/[shareModule]
 * (which both depend on it) resolve their dependencies. Likewise [instagramAuthModule] must be
 * present before [videoProcessingModule]/[shareModule] (which both depend on it). Koin doesn't
 * require module ordering, but listed here in dependency order for readability.
 */
class InstaMapsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@InstaMapsApplication)
            modules(
                commonModule,
                settingsModule,
                instagramAuthModule,
                historyModule,
                mapsModule,
                geocodingModule,
                videoProcessingModule,
                shareModule,
                settingsUiModule,
                instagramAuthUiModule,
                historyUiModule,
                permissionsUiModule,
            )
        }
    }
}
