package com.stax.feature.onboarding.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** First-run onboarding flow. */
@Serializable
data object OnboardingRoute : NavKey

/** Notification-permission gate, shown after onboarding when `POST_NOTIFICATIONS` is not granted (§4.15). */
@Serializable
data object NotificationGateRoute : NavKey
