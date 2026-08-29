package com.stax.feature.sites.presentation.di

import com.stax.feature.sites.presentation.SitesViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sitesPresentationModule = module {
    // Not `viewModelOf`: the clock and time zone are defaulted constructor parameters (they exist so
    // §4.12's cooldowns are testable), and the reflective form cannot pick an overload past them.
    viewModel { SitesViewModel(get(), get()) }
}
