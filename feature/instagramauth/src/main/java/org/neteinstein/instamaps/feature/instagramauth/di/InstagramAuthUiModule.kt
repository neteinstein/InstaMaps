package org.neteinstein.instamaps.feature.instagramauth.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.instagramauth.presentation.InstagramLoginViewModel

/**
 * Named `instagramAuthUiModule` (not `instagramAuthModule`) to avoid clashing with
 * `core:instagramauth`'s `instagramAuthModule` when both are listed side by side in
 * `InstaMapsApplication`'s Koin setup - mirrors `feature:settings`'s `settingsUiModule`.
 */
val instagramAuthUiModule =
    module {
        viewModel {
            InstagramLoginViewModel(saveInstagramSessionUseCase = get())
        }
    }
