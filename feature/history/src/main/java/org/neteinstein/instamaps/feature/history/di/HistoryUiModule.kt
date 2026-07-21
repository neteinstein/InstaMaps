package org.neteinstein.instamaps.feature.history.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.neteinstein.instamaps.feature.history.presentation.HistoryViewModel

/**
 * Named `historyUiModule` (not `historyModule`) to avoid clashing with `core:history`'s
 * `historyModule` when both are listed side by side in `InstaMapsApplication`'s Koin setup.
 */
val historyUiModule =
    module {
        viewModel {
            HistoryViewModel(observeHistoryUseCase = get())
        }
    }
