package io.stax.health.core.di

import io.stax.health.features.dashboard.presentation.DashboardViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dashboardModule =
    module {
        viewModelOf(::DashboardViewModel)
    }

val appModule =
    module {
        // Core dependencies will go here
    }
