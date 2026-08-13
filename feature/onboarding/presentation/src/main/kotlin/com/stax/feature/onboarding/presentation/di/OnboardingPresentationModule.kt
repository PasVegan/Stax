package com.stax.feature.onboarding.presentation.di

import com.stax.feature.onboarding.presentation.completion.OnboardingCompletionViewModel
import com.stax.feature.onboarding.presentation.notificationgate.NotificationGateViewModel
import com.stax.feature.onboarding.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingPresentationModule = module {
    // ViewModels added as screens are implemented.
    // Use viewModelOf(::MyViewModel) form exclusively.
    viewModelOf(::WelcomeViewModel)
    viewModelOf(::OnboardingCompletionViewModel)
    viewModelOf(::NotificationGateViewModel)
}
