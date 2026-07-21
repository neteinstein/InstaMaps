package org.neteinstein.instamaps.feature.settings.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.settings.presentation.SettingsViewModel

/**
 * Named `settingsUiModule` (not `settingsModule`) to avoid clashing with `core:settings`'s
 * `settingsModule` when both are listed side by side in `InstaMapsApplication`'s Koin setup.
 */
val settingsUiModule =
    module {
        viewModel {
            SettingsViewModel(
                observeGeminiApiKeyUseCase = get(),
                saveGeminiApiKeyUseCase = get(),
                checkForUpdateUseCase = get(),
                downloadAppUpdateUseCase = get(),
                appUpdateInstaller = get(),
            )
        }
    }
