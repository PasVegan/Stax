package com.stax.feature.compounds.presentation.di

import com.stax.feature.compounds.presentation.list.CompoundsListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val compoundsPresentationModule = module {
    // ViewModels added as screens are implemented. Prefer the viewModelOf(::MyViewModel) form;
    // spell the constructor out only to keep a production date/clock default, as :core:data does.
    viewModel { CompoundsListViewModel(get(), get()) }
}
