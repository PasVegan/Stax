/**
 * Onboarding step 1 — the Welcome screen (§4.14 step 1, §6.4.2 Onboarding).
 *
 * Entry point: `WelcomeScreen`. It carries no state of its own — the step is a static introduction
 * whose only outputs are "continue" and "skip" — so it is a plain composable driven by lambdas the
 * `:app` `entryProvider` supplies, with no ViewModel. Steps 2 and 3 reuse the Create Compound /
 * Create Protocol screens (M6-02 / M6-03) and bring their own ViewModels with them.
 */
package com.stax.feature.onboarding.presentation.welcome
