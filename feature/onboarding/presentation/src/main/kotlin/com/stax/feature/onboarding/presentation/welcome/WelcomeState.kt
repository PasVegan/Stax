package com.stax.feature.onboarding.presentation.welcome

/**
 * UI state of the Welcome step (§4.14 step 1).
 *
 * The position in the flow is state rather than a constant in the composable because steps 2 and 3
 * show the same progress (M6-02 / M6-03) and the pill row must stay in sync with wherever the user
 * actually is.
 */
data class WelcomeState(val currentStep: Int = WELCOME_STEP, val stepCount: Int = ONBOARDING_STEP_COUNT)

/** Welcome is step 1 of the 3-step onboarding flow (§4.14). */
private const val WELCOME_STEP = 1
private const val ONBOARDING_STEP_COUNT = 3
