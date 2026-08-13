/**
 * Navigation 3 typed routes (`OnboardingRoute`, `NotificationGateRoute`) and the
 * [com.stax.feature.onboarding.presentation.navigation.onboardingEntries] entryProvider extension
 * for the Onboarding feature.
 *
 * Boundaries: routes are `@Serializable` `NavKey`s only; no Room, no `NavController`/`NavHost`, and
 * never another feature's routes. Cross-feature navigation is wired as lambda callbacks in `:app`.
 *
 * Entry points: `onboardingEntries(...)`.
 */
package com.stax.feature.onboarding.presentation.navigation
