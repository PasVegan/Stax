package com.stax.feature.protocols.presentation.di

import com.stax.feature.protocols.presentation.detail.ProtocolDetailArgs
import com.stax.feature.protocols.presentation.detail.ProtocolDetailViewModel
import com.stax.feature.protocols.presentation.form.ProtocolFormArgs
import com.stax.feature.protocols.presentation.form.ProtocolFormViewModel
import com.stax.feature.protocols.presentation.list.ProtocolsListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val protocolsPresentationModule = module {
    // ViewModels added as screens are implemented. Prefer the viewModelOf(::MyViewModel) form;
    // spell the constructor out only to keep a production date/clock default, as :core:data does.
    viewModel { ProtocolsListViewModel(get(), get(), get()) }

    // Create and Edit are one screen (§4.9), so which protocol it is on arrives as a Koin parameter,
    // alongside the `SavedStateHandle` behind the auto-saved draft — both read off the parameter holder.
    viewModel { params ->
        ProtocolFormViewModel(params.get(), get(), get(), get(), params.get<ProtocolFormArgs>())
    }

    // Which protocol Detail is on arrives as a Koin parameter, like the form's.
    viewModel { params ->
        ProtocolDetailViewModel(get(), get(), get(), get(), get(), params.get<ProtocolDetailArgs>())
    }
}
