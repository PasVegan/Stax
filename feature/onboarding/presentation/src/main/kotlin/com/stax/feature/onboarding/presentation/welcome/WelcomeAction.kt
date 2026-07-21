package com.stax.feature.onboarding.presentation.welcome

/** Everything the user can do on the Welcome step (§4.14 step 1). */
sealed interface WelcomeAction {
    data object OnContinueClick : WelcomeAction
    data object OnSkipClick : WelcomeAction
}
