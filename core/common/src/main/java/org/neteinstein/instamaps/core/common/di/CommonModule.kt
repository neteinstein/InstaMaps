package org.neteinstein.instamaps.core.common.di

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.neteinstein.instamaps.core.common.DefaultDispatcherProvider
import org.neteinstein.instamaps.core.common.DispatcherProvider

val commonModule =
    module {
        singleOf(::DefaultDispatcherProvider) { bind<DispatcherProvider>() }
    }
