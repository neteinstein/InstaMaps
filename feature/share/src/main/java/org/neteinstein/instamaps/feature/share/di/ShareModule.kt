package org.neteinstein.instamaps.feature.share.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.share.domain.ParseSharedTextUseCase
import org.neteinstein.instamaps.feature.share.domain.ProcessSharedUrlUseCase
import org.neteinstein.instamaps.feature.share.presentation.ShareViewModel
import org.neteinstein.instamaps.feature.share.work.ShareNotifier

/**
 * [ShareNotifier] is registered here (not constructed directly by [org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker])
 * so both the Worker (via `KoinComponent`/`by inject()`, since `WorkManager`'s default factory can't
 * take extra constructor params) and any future caller share the same instance/channel setup.
 */
val shareModule =
    module {
        factory { ParseSharedTextUseCase() }
        factory {
            ProcessSharedUrlUseCase(
                collectAllTextUseCase = get(),
                resolveLocationUseCase = get(),
            )
        }
        single { ShareNotifier(context = androidContext()) }
        viewModel {
            ShareViewModel(
                context = androidContext(),
                parseSharedTextUseCase = get(),
                observeInstagramAuthStateUseCase = get(),
            )
        }
    }
