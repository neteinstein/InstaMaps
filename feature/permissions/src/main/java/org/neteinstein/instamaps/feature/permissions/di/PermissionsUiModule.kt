package org.neteinstein.instamaps.feature.permissions.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.permissions.presentation.PermissionsViewModel

val permissionsUiModule =
    module {
        viewModel {
            PermissionsViewModel(
                isGeminiApiKeyConfiguredUseCase = get(),
            )
        }
    }
