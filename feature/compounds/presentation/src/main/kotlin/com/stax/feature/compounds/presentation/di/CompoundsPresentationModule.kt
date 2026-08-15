package com.stax.feature.compounds.presentation.di

import com.stax.feature.compounds.presentation.form.CompoundFormArgs
import com.stax.feature.compounds.presentation.form.CompoundFormViewModel
import com.stax.feature.compounds.presentation.list.CompoundsListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val compoundsPresentationModule = module {
    // ViewModels added as screens are implemented. Prefer the viewModelOf(::MyViewModel) form;
    // spell the constructor out only to keep a production date/clock default, as :core:data does.
    viewModel { CompoundsListViewModel(get(), get()) }

    // The form's route arguments arrive as a Koin parameter (§4.4: Create and Edit are one screen),
    // and the `SavedStateHandle` behind the auto-saved draft (§4.4.5) is resolved from the
    // ViewModel's CreationExtras — which is why both are read off the parameter holder.
    viewModel { params -> CompoundFormViewModel(params.get(), get(), params.get<CompoundFormArgs>()) }
}
