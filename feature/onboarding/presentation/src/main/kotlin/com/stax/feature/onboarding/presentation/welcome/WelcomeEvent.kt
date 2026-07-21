package com.stax.feature.onboarding.presentation.welcome

/**
 * One-time effects of the Welcome step (§4.14 step 1). Both are navigation intents — *what* the user
 * asked for, not where it leads; `:app` owns the destination (§10.3).
 */
sealed interface WelcomeEvent {
    /** Advance the flow — step 2 reuses Create Compound (M6-02). */
    data object NavigateToNextStep : WelcomeEvent

    /** Leave onboarding without completing it. */
    data object SkipOnboarding : WelcomeEvent
}
