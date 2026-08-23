package com.stax.feature.reconstitution.presentation.di

import com.stax.feature.reconstitution.presentation.ReconstitutionArgs
import com.stax.feature.reconstitution.presentation.ReconstitutionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reconstitutionPresentationModule = module {
    // The helper's route argument arrives as a Koin parameter: which compound it was opened on — if
    // any — is not something it can look up (§4.6).
    viewModel { params -> ReconstitutionViewModel(get(), params.get<ReconstitutionArgs>()) }
}
