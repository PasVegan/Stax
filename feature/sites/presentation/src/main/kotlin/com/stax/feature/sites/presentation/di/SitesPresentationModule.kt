package com.stax.feature.sites.presentation.di

import com.stax.feature.sites.presentation.SitesViewModel
import com.stax.feature.sites.presentation.picker.SitePickerArgs
import com.stax.feature.sites.presentation.picker.SitePickerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sitesPresentationModule = module {
    // Not `viewModelOf`: the clock and time zone are defaulted constructor parameters (they exist so
    // §4.12's cooldowns are testable), and the reflective form cannot pick an overload past them.
    viewModel { SitesViewModel(get(), get()) }

    // §4.12.7's picker takes its route arguments as a Koin parameter, and the `SavedStateHandle`
    // holding the in-flight selection is resolved from the ViewModel's CreationExtras — which is why
    // both are read off the parameter holder.
    viewModel { params -> SitePickerViewModel(params.get(), get(), params.get<SitePickerArgs>()) }
}
