package com.stax.feature.onboarding.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.onboarding.presentation.welcome.WelcomeScreen

/**
 * Contributes the Onboarding `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Where each action leads is `:app`'s decision, expressed as lambdas (spec §10.3): [onContinue]
 * advances the flow — step 2 reuses Create Compound (M6-02) — and [onSkip] leaves it. This module
 * references no other feature's routes.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(onContinue: () -> Unit, onSkip: () -> Unit) {
    entry<OnboardingRoute> {
        WelcomeScreen(onContinue = onContinue, onSkip = onSkip)
    }
}
