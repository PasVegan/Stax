package com.stax.feature.onboarding.presentation.completion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.androidx.compose.koinViewModel

/**
 * Remembers the callback that ends the onboarding flow (§4.14) by persisting
 * `Settings.onboardingCompleted = true`.
 *
 * The last step reuses another feature's screen, so the tap that finishes onboarding happens outside
 * this module. `:app` wires that tap back to here — features never depend on features (§10.3) — and
 * pairs it with the navigation to Dashboard, which is `:app`'s call to make.
 */
@Composable
fun rememberOnboardingCompletion(): () -> Unit {
    val viewModel = koinViewModel<OnboardingCompletionViewModel>()
    return remember(viewModel) { viewModel::complete }
}
