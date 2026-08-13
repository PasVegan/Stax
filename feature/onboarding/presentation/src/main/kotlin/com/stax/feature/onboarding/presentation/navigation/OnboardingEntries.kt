package com.stax.feature.onboarding.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stax.feature.onboarding.presentation.notificationgate.NotificationGateRoot
import com.stax.feature.onboarding.presentation.welcome.WelcomeRoot

/**
 * Contributes the Onboarding `NavEntry`s to the app's `NavDisplay` `entryProvider`.
 *
 * Where each action leads is `:app`'s decision, expressed as lambdas (spec §10.3): [onContinue]
 * advances the flow — step 2 reuses Create Compound (M6-02) — [onSkip] leaves it, and
 * [onNotificationGateProceed] is the gate's exit once the user has answered it (§4.15). This module
 * references no other feature's routes.
 */
fun EntryProviderScope<NavKey>.onboardingEntries(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onNotificationGateProceed: () -> Unit,
) {
    entry<OnboardingRoute> {
        WelcomeRoot(onContinue = onContinue, onSkip = onSkip)
    }
    entry<NotificationGateRoute> {
        NotificationGateRoot(onProceed = onNotificationGateProceed)
    }
}
