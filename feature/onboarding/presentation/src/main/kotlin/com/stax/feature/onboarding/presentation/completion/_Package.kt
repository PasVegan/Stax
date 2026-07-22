/**
 * The end of the onboarding flow (§4.14): persisting `Settings.onboardingCompleted = true`.
 *
 * Steps 2 and 3 reuse the Create Compound / Create Protocol screens, so onboarding finishes on a
 * screen this module does not own. `rememberOnboardingCompletion` is the seam: `:app` hands the
 * finishing tap back here for the write, and keeps the navigation to Dashboard for itself (§10.3).
 *
 * `OnboardingCompletionViewModel` is deliberately not an MVI screen triple — see its KDoc.
 */
package com.stax.feature.onboarding.presentation.completion
